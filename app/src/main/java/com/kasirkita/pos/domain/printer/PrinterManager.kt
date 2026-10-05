package com.kasirkita.pos.domain.printer

import android.annotation.SuppressLint
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinated printer hardware operations.
 */
@Singleton
class PrinterManager @Inject constructor(
    private val configDataStore: PrinterConfigDataStore,
    private val transport: BluetoothPrinterTransport,
    private val formatter: EscPosReceiptFormatter,
) {
    /**
     * Test printer with diagnostic receipt.
     * Does NOT open drawer.
     */
    suspend fun testPrinter(): Result<Unit> {
        val config = configDataStore.configFlow.first()

        val deviceAddress = config.deviceAddress
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val testReceipt = buildString {
            append(ESC_INIT)
            append(ESC_ALIGN_CENTER)
            append("KasirKita\n")
            append("Test Printer\n")
            append(config.deviceName ?: "Printer\n")
            append("${config.paperWidthMm}mm\n")
            append(java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("id", "ID"))
                .format(java.util.Date()))
            append("\n\n")
            append(ESC_FEED_CUT)
        }

        return transport.print(deviceAddress, testReceipt.toByteArray(Charsets.UTF_8))
    }

    /**
     * Open cash drawer via drawer pulse command.
     * Used for admin Test Drawer action.
     */
    suspend fun testDrawer(): Result<Unit> {
        val config = configDataStore.configFlow.first()

        val deviceAddress = config.deviceAddress
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val pulseBytes = formatter.formatDrawerPulse(config.drawerPulseProfile)

        return transport.print(deviceAddress, pulseBytes)
    }

    /**
     * Get paired Bluetooth devices.
     * Paired only, no scanning.
     */
    @SuppressLint("MissingPermission")
    suspend fun getPairedDevices(): Result<List<PrinterDevice>> {
        return transport.getPairedDevices().map { devices ->
            devices.map { device ->
                PrinterDevice(
                    address = device.address,
                    name = device.name ?: device.address,
                )
            }
        }
    }

    data class PrinterDevice(
        val address: String,
        val name: String,
    )

    private companion object {
        const val ESC_INIT = "\u001B@"
        const val ESC_ALIGN_CENTER = "\u001Ba\u0001"
        const val ESC_FEED_CUT = "\n\n\n\u001Bd\u0005\u001Bm"
    }
}