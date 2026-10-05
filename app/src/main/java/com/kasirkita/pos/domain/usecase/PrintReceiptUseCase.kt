package com.kasirkita.pos.domain.usecase

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
        val config = configDataStore?.configFlow?.first()
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val deviceAddress = config.deviceAddress
            ?: return Result.failure(Exception("Printer belum dikonfigurasi"))

        val data = formatter?.formatReceipt(receipt, config.paperWidthMm)
            ?: return Result.failure(Exception("Formatter unavailable"))

        return transport?.print(deviceAddress, data)
            ?: Result.failure(Exception("Transport unavailable"))
    }
}
