package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.printer.CashDrawerController
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Orchestrate post-sale hardware actions.
 * Independent of transaction state.
 * - Auto-print receipt if configured
 * - Auto-open drawer if eligible
 * Should be called after authoritative online transaction success.
 */
open class PrintAfterCheckoutUseCase @Inject constructor(
    private val configDataStore: PrinterConfigDataStore?,
    private val transport: BluetoothPrinterTransport?,
    private val formatter: EscPosReceiptFormatter?,
    private val cashDrawerController: CashDrawerController?,
) {

    /**
     * No-arg constructor for unit testing only.
     * Subclasses that override invoke() do not need real dependencies.
     */
    protected constructor() : this(null, null, null, null)

    sealed interface Result {
        data class Success(val printed: Boolean, val drawerOpened: Boolean) : Result
        data class Failure(
            val transactionSuccessful: Boolean, // Must remain true
            val printError: String?,
            val drawerError: String?,
        ) : Result
    }

    open suspend operator fun invoke(
        receipt: Receipt,
        paymentMethod: String,
        isOriginalOnlineCheckout: Boolean,
    ): Result {
        val config = configDataStore?.configFlow?.first() ?: return Result.Success(false, false)

        val printerConfigured = config.deviceAddress != null

        var printed = false
        var drawerOpened = false
        var printError: String? = null
        var drawerError: String? = null

        // Auto-print receipt if configured
        if (printerConfigured && config.autoPrint && formatter != null && transport != null) {
            try {
                val data = formatter.formatReceipt(receipt, config.paperWidthMm)
                transport.print(config.deviceAddress!!, data).getOrThrow()
                printed = true
            } catch (e: Exception) {
                printError = e.message
                // Continue - transaction success unaffected
            }
        }

        // Auto-open drawer if eligible
        if (printerConfigured && config.autoDrawer && cashDrawerController != null && formatter != null && transport != null) {
            val eligible = cashDrawerController.isDrawerEligible(
                isOriginalOnlineCheckout = isOriginalOnlineCheckout,
                paymentMethod = paymentMethod,
                printerConfigured = printerConfigured,
                autoDrawerEnabled = config.autoDrawer,
            )

            if (eligible) {
                try {
                    val pulseBytes = formatter.formatDrawerPulse(config.drawerPulseProfile)
                    transport.print(config.deviceAddress!!, pulseBytes).getOrThrow()
                    drawerOpened = true
                } catch (e: Exception) {
                    drawerError = e.message
                    // Continue - transaction success unaffected
                }
            }
        }

        return if (printError == null && drawerError == null) {
            Result.Success(printed, drawerOpened)
        } else {
            Result.Failure(
                transactionSuccessful = true,
                printError = printError,
                drawerError = drawerError,
            )
        }
    }
}