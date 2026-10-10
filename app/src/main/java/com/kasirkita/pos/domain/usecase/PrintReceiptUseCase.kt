package com.kasirkita.pos.domain.usecase

import android.util.Log
import com.kasirkita.pos.BuildConfig
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptDocument
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
        documentBuilder: BuildReceiptDocumentUseCase,
    ) : this(
        Operations(
            configProvider = { configDataStore.configFlow.first() },
            documentBuilder = documentBuilder::invoke,
            formatter = formatter::formatReceipt,
            transport = { address, data -> transport.print(address, data) },
            settingsResolver = settingsResolver::invoke,
        ),
    )

    internal constructor(
        configProvider: suspend () -> PrinterConfig,
        documentBuilder: (Receipt, ReceiptSettings?, Int) -> ReceiptDocument = BuildReceiptDocumentUseCase()::invoke,
        formatter: (ReceiptDocument) -> ByteArray,
        transport: suspend (String, ByteArray) -> Result<Unit>,
        settingsResolver: suspend (Receipt) -> ResolveReceiptSettingsUseCase.Resolution,
    ) : this(
        Operations(
            configProvider = configProvider,
            documentBuilder = documentBuilder,
            formatter = formatter,
            transport = transport,
            settingsResolver = settingsResolver,
        ),
    )

    /**
     * Unit-test subclass constructor. The default operations fail loudly if a subclass does not override invoke().
     */
    protected constructor() : this(Operations.throwing())

    /** Canonical document snapshot shared by preview and manual printing. */
    class ValidatedPrintContext internal constructor(
        val document: ReceiptDocument,
        val usedLegacyFallback: Boolean,
    )

    /**
     * Builds a manual-print context from the same resolution used for preview.
     */
    fun validatedContext(
        receipt: Receipt,
        resolution: ResolveReceiptSettingsUseCase.Resolution,
        document: ReceiptDocument,
    ): Result<ValidatedPrintContext> = validateContext(
        receipt = receipt,
        settings = resolution.settings,
        document = document,
        usedLegacyFallback = resolution.usedLegacyFallback,
    )

    /** Prints a context that was already validated while building the preview. Does not resolve settings again. */
    open suspend fun invokeResolved(context: ValidatedPrintContext): Result<Unit> =
        invokeInternal(context)

    /** Resolves settings from the receipt's immutable tenant/outlet identity, then prints. */
    open suspend operator fun invoke(receipt: Receipt): Result<Unit> {
        val config = operations.configProvider()
        val resolution = operations.settingsResolver(receipt)
        val document = operations.documentBuilder(receipt, resolution.settings, config.paperWidthMm)
        val context = validateContext(
            receipt = receipt,
            settings = resolution.settings,
            document = document,
            usedLegacyFallback = resolution.usedLegacyFallback,
        ).getOrElse { return Result.failure(it) }
        return invokeInternal(context, configOverride = config)
    }

    private suspend fun invokeInternal(
        context: ValidatedPrintContext,
        configOverride: PrinterConfig? = null,
    ): Result<Unit> {
        val totalStartedNanos = System.nanoTime()
        val ops = operations
        val config = configOverride ?: ops.configProvider()
        val deviceAddress = config.deviceAddress
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val formatStartedNanos = System.nanoTime()
        val data = ops.formatter(context.document)
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
        document: ReceiptDocument,
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
                document = document,
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
        val documentBuilder: (Receipt, ReceiptSettings?, Int) -> ReceiptDocument,
        val formatter: (ReceiptDocument) -> ByteArray,
        val transport: suspend (String, ByteArray) -> Result<Unit>,
        val settingsResolver: suspend (Receipt) -> ResolveReceiptSettingsUseCase.Resolution,
    ) {
        companion object {
            fun throwing() = Operations(
                configProvider = { error("PrintReceiptUseCase config provider is not available") },
                documentBuilder = { _, _, _ -> error("PrintReceiptUseCase document builder is not available") },
                formatter = { error("PrintReceiptUseCase formatter is not available") },
                transport = { _, _ -> error("PrintReceiptUseCase transport is not available") },
                settingsResolver = { error("PrintReceiptUseCase settings resolver is not available") },
            )
        }
    }

    private companion object {
        const val PRINT_TIMING_TAG = "PrintTiming"
    }
}
