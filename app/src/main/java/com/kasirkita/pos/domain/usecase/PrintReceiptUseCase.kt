package com.kasirkita.pos.domain.usecase

import android.util.Log
import com.kasirkita.pos.BuildConfig
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptSettings
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Print receipt using current printer configuration.
 * Used by ReceiptScreen manual print and transaction reprint. Checkout auto-print uses PrintAfterCheckoutUseCase.
 */
open class PrintReceiptUseCase private constructor(
    private val operations: Operations,
) {
    @Inject
    constructor(
        configDataStore: PrinterConfigDataStore,
        formatter: EscPosReceiptFormatter,
        transport: BluetoothPrinterTransport,
        settingsResolver: ResolveReceiptSettingsUseCase,
    ) : this(
        Operations(
            configProvider = { configDataStore.configFlow.first() },
            formatter = { receipt, settings, paperWidthMm ->
                formatter.formatReceipt(receipt, settings, paperWidthMm)
            },
            transport = { address, data -> transport.print(address, data) },
            settingsResolver = settingsResolver::invoke,
        ),
    )

    internal constructor(
        configProvider: suspend () -> PrinterConfig,
        formatter: (Receipt, ReceiptSettings?, Int) -> ByteArray,
        transport: suspend (String, ByteArray) -> Result<Unit>,
        settingsResolver: suspend (Receipt) -> ResolveReceiptSettingsUseCase.Resolution,
    ) : this(
        Operations(
            configProvider = configProvider,
            formatter = formatter,
            transport = transport,
            settingsResolver = settingsResolver,
        ),
    )

    /**
     * Unit-test subclass constructor. The default operations fail loudly if a subclass does not override invoke().
     */
    protected constructor() : this(Operations.throwing())

    /**
     * Validated preview/print context. Non-null settings are guaranteed to match the receipt tenant/outlet.
     * A null settings value is allowed only as the explicit result of completed legacy fallback resolution.
     */
    data class ValidatedPrintContext internal constructor(
        val receipt: Receipt,
        val settings: ReceiptSettings?,
        val paperWidthMm: Int,
        val usedLegacyFallback: Boolean,
    )

    /**
     * Builds a manual-print context from the same resolution used for preview.
     */
    fun validatedContext(
        receipt: Receipt,
        resolution: ResolveReceiptSettingsUseCase.Resolution,
        paperWidthMm: Int,
    ): Result<ValidatedPrintContext> = validateContext(
        receipt = receipt,
        settings = resolution.settings,
        paperWidthMm = paperWidthMm,
        usedLegacyFallback = resolution.usedLegacyFallback,
    )

    /** Prints a context that was already validated while building the preview. Does not resolve settings again. */
    open suspend fun invokeResolved(context: ValidatedPrintContext): Result<Unit> =
        invokeInternal(context, resolveSettings = false)

    /** Resolves settings from the receipt's immutable tenant/outlet identity, then prints. */
    open suspend operator fun invoke(receipt: Receipt): Result<Unit> {
        val config = operations.configProvider()
        val resolution = operations.settingsResolver(receipt)
        val context = validateContext(
            receipt = receipt,
            settings = resolution.settings,
            paperWidthMm = config.paperWidthMm,
            usedLegacyFallback = resolution.usedLegacyFallback,
        ).getOrElse { return Result.failure(it) }
        return invokeInternal(context, resolveSettings = false, configOverride = config)
    }

    private suspend fun invokeInternal(
        context: ValidatedPrintContext,
        resolveSettings: Boolean,
        configOverride: PrinterConfig? = null,
    ): Result<Unit> {
        val totalStartedNanos = System.nanoTime()
        val ops = operations
        val config = configOverride ?: ops.configProvider()
        val deviceAddress = config.deviceAddress
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val effectiveContext = if (resolveSettings) {
            val resolution = ops.settingsResolver(context.receipt)
            validateContext(
                receipt = context.receipt,
                settings = resolution.settings,
                paperWidthMm = config.paperWidthMm,
                usedLegacyFallback = resolution.usedLegacyFallback,
            ).getOrElse { return Result.failure(it) }
        } else {
            context
        }

        val formatStartedNanos = System.nanoTime()
        val data = ops.formatter(
            effectiveContext.receipt,
            effectiveContext.settings,
            effectiveContext.paperWidthMm,
        )
        if (BuildConfig.DEBUG) {
            debugLog("format_ms=${millisSince(formatStartedNanos)}")
        }

        val result = ops.transport(deviceAddress, data)

        if (BuildConfig.DEBUG) {
            debugLog("total_ms=${millisSince(totalStartedNanos)} success=${result.isSuccess}")
        }
        return result
    }

    private fun validateContext(
        receipt: Receipt,
        settings: ReceiptSettings?,
        paperWidthMm: Int,
        usedLegacyFallback: Boolean,
    ): Result<ValidatedPrintContext> {
        if (settings != null) {
            if (settings.tenantId != receipt.tenant.id) {
                return Result.failure(IllegalArgumentException("Receipt settings tenant mismatch"))
            }
            if (settings.outletId != receipt.outlet.id) {
                return Result.failure(IllegalArgumentException("Receipt settings outlet mismatch"))
            }
        }
        return Result.success(
            ValidatedPrintContext(
                receipt = receipt,
                settings = settings,
                paperWidthMm = paperWidthMm,
                usedLegacyFallback = usedLegacyFallback,
            ),
        )
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(PRINT_TIMING_TAG, message) }
    }

    private fun millisSince(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000

    private data class Operations(
        val configProvider: suspend () -> PrinterConfig,
        val formatter: (Receipt, ReceiptSettings?, Int) -> ByteArray,
        val transport: suspend (String, ByteArray) -> Result<Unit>,
        val settingsResolver: suspend (Receipt) -> ResolveReceiptSettingsUseCase.Resolution,
    ) {
        companion object {
            fun throwing() = Operations(
                configProvider = { error("PrintReceiptUseCase config provider is not available") },
                formatter = { _, _, _ -> error("PrintReceiptUseCase formatter is not available") },
                transport = { _, _ -> error("PrintReceiptUseCase transport is not available") },
                settingsResolver = { error("PrintReceiptUseCase settings resolver is not available") },
            )
        }
    }

    private companion object {
        const val PRINT_TIMING_TAG = "PrintTiming"
    }
}