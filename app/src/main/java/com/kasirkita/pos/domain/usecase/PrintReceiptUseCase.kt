package com.kasirkita.pos.domain.usecase

import android.util.Log
import com.kasirkita.pos.BuildConfig
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.printer.PrinterManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Print receipt using current printer configuration.
 * Used by ReceiptScreen manual print and auto-print.
 */
open class PrintReceiptUseCase @Inject constructor(
    private val configDataStore: PrinterConfigDataStore?,
    private val formatter: EscPosReceiptFormatter?,
    private val transport: BluetoothPrinterTransport?,
) {
    /**
     * No-arg constructor for unit testing only.
     * Subclasses that override invoke() do not need real dependencies.
     */
    protected constructor() : this(null, null, null)

    open suspend operator fun invoke(receipt: Receipt): Result<Unit> {
        val totalStartedNanos = System.nanoTime()
        val config = configDataStore?.configFlow?.first()
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val deviceAddress = config.deviceAddress
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val formatStartedNanos = System.nanoTime()
        val data = formatter?.formatReceipt(receipt, config.paperWidthMm)
            ?: return Result.failure(Exception("Formatter unavailable"))
        if (BuildConfig.DEBUG) {
            debugLog("format_ms=${millisSince(formatStartedNanos)}")
        }

        val result = transport?.print(deviceAddress, data)
            ?: return Result.failure(Exception("Transport unavailable"))

        if (BuildConfig.DEBUG) {
            debugLog("total_ms=${millisSince(totalStartedNanos)} success=${result.isSuccess}")
        }
        return result
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(PRINT_TIMING_TAG, message) }
    }

    private fun millisSince(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val PRINT_TIMING_TAG = "PrintTiming"
    }
}
