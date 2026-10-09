package com.kasirkita.pos.domain.usecase

import android.util.Log
import com.kasirkita.pos.BuildConfig
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.printer.CashDrawerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Prints a receipt after checkout. Manual print and reprint use PrintReceiptUseCase.
 */
open class PrintAfterCheckoutUseCase private constructor(
    private val operations: Operations,
) {
    @Inject
    constructor(
        configDataStore: PrinterConfigDataStore,
        formatter: EscPosReceiptFormatter,
        transport: BluetoothPrinterTransport,
        settingsResolver: ResolveReceiptSettingsUseCase,
        drawerController: CashDrawerController,
    ) : this(
        Operations(
            configProvider = { configDataStore.configFlow.first() },
            receiptFormatter = formatter::formatReceipt,
            drawerFormatter = formatter::formatDrawerPulse,
            transport = transport::print,
            settingsResolver = settingsResolver::invoke,
            drawerEligible = drawerController::isDrawerEligible,
        ),
    )

    internal constructor(
        configProvider: suspend () -> PrinterConfig,
        receiptFormatter: (Receipt, ReceiptSettings?, Int) -> ByteArray,
        drawerFormatter: (DrawerPulseProfile) -> ByteArray,
        transport: suspend (String, ByteArray) -> kotlin.Result<Unit>,
        settingsResolver: suspend (Receipt) -> ResolveReceiptSettingsUseCase.Resolution,
        drawerEligible: (Boolean, String, Boolean, Boolean) -> Boolean,
    ) : this(
        Operations(
            configProvider,
            receiptFormatter,
            drawerFormatter,
            transport,
            settingsResolver,
            drawerEligible,
        ),
    )

    /** Test-subclass seam. Any non-overridden operation fails loudly. */
    protected constructor() : this(Operations.throwing())

    open suspend operator fun invoke(
        receipt: Receipt,
        paymentMethod: String,
        isOriginalOnlineCheckout: Boolean,
    ): Result {
        val startedNanos = System.nanoTime()
        val config = operations.configProvider()
        val address = config.deviceAddress
        val printerConfigured = address != null
        var printed = false
        var drawerOpened = false
        var printError: String? = null
        var drawerError: String? = null

        if (printerConfigured && config.autoPrint) {
            try {
                val resolution = try {
                    operations.settingsResolver(receipt)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    ResolveReceiptSettingsUseCase.Resolution(null, usedLegacyFallback = true)
                }
                val receiptBytes = operations.receiptFormatter(
                    receipt,
                    resolution.settings,
                    config.paperWidthMm,
                )
                operations.transport(address!!, receiptBytes).getOrThrow()
                printed = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                printError = error.message
            }
        }

        if (operations.drawerEligible(
                isOriginalOnlineCheckout,
                paymentMethod,
                printerConfigured,
                config.autoDrawer,
            )
        ) {
            try {
                val drawerBytes = operations.drawerFormatter(config.drawerPulseProfile)
                operations.transport(address!!, drawerBytes).getOrThrow()
                drawerOpened = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                drawerError = error.message
            }
        }

        if (BuildConfig.DEBUG) {
            runCatching {
                Log.d(PRINT_TIMING_TAG, "total_ms=${millisSince(startedNanos)} success=${printError == null && drawerError == null}")
            }
        }
        return if (printError == null && drawerError == null) {
            Result.Success(printed = printed, drawerOpened = drawerOpened)
        } else {
            Result.Failure(
                transactionSuccessful = true,
                printError = printError,
                drawerError = drawerError,
            )
        }
    }

    sealed interface Result {
        data class Success(val printed: Boolean, val drawerOpened: Boolean) : Result
        data class Failure(
            val transactionSuccessful: Boolean,
            val printError: String?,
            val drawerError: String?,
        ) : Result
    }

    private data class Operations(
        val configProvider: suspend () -> PrinterConfig,
        val receiptFormatter: (Receipt, ReceiptSettings?, Int) -> ByteArray,
        val drawerFormatter: (DrawerPulseProfile) -> ByteArray,
        val transport: suspend (String, ByteArray) -> kotlin.Result<Unit>,
        val settingsResolver: suspend (Receipt) -> ResolveReceiptSettingsUseCase.Resolution,
        val drawerEligible: (Boolean, String, Boolean, Boolean) -> Boolean,
    ) {
        companion object {
            fun throwing() = Operations(
                configProvider = { error("Missing config provider") },
                receiptFormatter = { _, _, _ -> error("Missing receipt formatter") },
                drawerFormatter = { error("Missing drawer formatter") },
                transport = { _, _ -> error("Missing printer transport") },
                settingsResolver = { error("Missing settings resolver") },
                drawerEligible = { _, _, _, _ -> error("Missing drawer policy") },
            )
        }
    }

    private fun millisSince(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val PRINT_TIMING_TAG = "PrintTiming"
    }
}
