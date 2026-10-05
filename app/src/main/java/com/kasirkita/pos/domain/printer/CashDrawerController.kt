package com.kasirkita.pos.domain.printer

import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import com.kasirkita.pos.domain.model.PrinterError
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cash drawer control with strict safety rules.
 * Drawer opens ONLY for original online CASH transactions when autoDrawer=true.
 */
@Singleton
class CashDrawerController @Inject constructor(
    private val configDataStore: PrinterConfigDataStore,
    private val transport: BluetoothPrinterTransport,
    private val formatter: EscPosReceiptFormatter,
) {
    /**
     * Evaluate drawer eligibility.
     * TRUE only if ALL conditions met:
     * - isOriginalOnlineCheckout (not reprint/reload/sync)
     * - payment method is CASH
     * - printer configured
     * - autoDrawer enabled
     */
    fun isDrawerEligible(
        isOriginalOnlineCheckout: Boolean,
        paymentMethod: String,
        printerConfigured: Boolean,
        autoDrawerEnabled: Boolean,
    ): Boolean {
        return isOriginalOnlineCheckout &&
            paymentMethod == "CASH" &&
            printerConfigured &&
            autoDrawerEnabled
    }

    /**
     * Open cash drawer.
     * Should only be called after drawer eligibility check OR explicit admin test.
     */
    suspend fun openDrawer(): Result<Unit> {
        val config = configDataStore.configFlow.first()

        val deviceAddress = config.deviceAddress
            ?: return Result.failure(Exception("NO_PRINTER_CONFIGURED"))

        val pulseBytes = formatter.formatDrawerPulse(config.drawerPulseProfile)

        return transport.print(deviceAddress, pulseBytes)
    }

    /**
     * Test drawer (admin action only).
     * Sends drawer pulse without checking eligibility rules.
     */
    suspend fun testDrawer(): Result<Unit> {
        return openDrawer()
    }
}
