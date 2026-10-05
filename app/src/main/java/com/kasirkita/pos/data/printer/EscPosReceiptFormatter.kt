package com.kasirkita.pos.data.printer

import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.Receipt
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic ESC/POS receipt formatter.
 * Physical compatibility with VSC TM-58D unverified until M16.
 */
@Singleton
class EscPosReceiptFormatter @Inject constructor() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))

    /**
     * Format receipt to ESC/POS byte array.
     * Uses canonical M14 Receipt domain snapshot.
     */
    fun formatReceipt(receipt: Receipt, paperWidthMm: Int): ByteArray {
        val maxChars = when (paperWidthMm) {
            58 -> 32
            80 -> 48
            else -> 32 // Default to 58mm
        }

        return buildString {
            // ESC/POS initialization
            append(ESC_INIT)

            // Center alignment for header
            append(ESC_ALIGN_CENTER)
            append(receipt.tenant.name.truncate(maxChars))
            append("\n")
            append(receipt.outlet.name.truncate(maxChars))
            append("\n")
            if (!receipt.outlet.address.isNullOrBlank()) {
                append(receipt.outlet.address.truncate(maxChars))
                append("\n")
            }
            append("\n")

            // Left alignment for body
            append(ESC_ALIGN_LEFT)

            // Transaction metadata
            append("ID: ${receipt.transactionId}\n")
            append("Tanggal: ${dateTimeFormat.format(Date())}\n")
            append("Kasir: ${receipt.cashier.name}\n")
            if (receipt.customer != null) {
                append("Pelanggan: ${receipt.customer.name}\n")
            }
            append(separator(maxChars))
            append("\n")

            // Items
            receipt.items.forEach { item ->
                // Product name (use snapshot if available)
                val productName = item.productNameSnapshot ?: item.productName
                append(productName.truncate(maxChars))
                append("\n")

                // SKU (use snapshot if available)
                val sku = item.skuSnapshot ?: item.sku
                if (sku.isNotBlank()) {
                    append("  SKU: $sku\n")
                }

                // Modifiers
                item.modifierSnapshots.forEach { modifier ->
                    append("  + ${modifier.groupName}: ${modifier.optionName}\n")
                }

                // Note
                if (!item.note.isNullOrBlank()) {
                    append("  Catatan: ${item.note}\n")
                }

                // Quantity x Unit Price = Subtotal
                val qtyLine = "${item.quantity} x ${formatCurrency(item.unitPrice)}"
                val subtotalLine = formatCurrency(item.subtotal)
                append(formatLine(qtyLine, subtotalLine, maxChars))
                append("\n")
            }

            append(separator(maxChars))
            append("\n")

            // Totals
            append(formatLine("Subtotal", formatCurrency(receipt.subtotal), maxChars))
            append("\n")

            if (receipt.discount > 0) {
                append(formatLine("Diskon", "-${formatCurrency(receipt.discount)}", maxChars))
                append("\n")
            }

            if (receipt.tax > 0) {
                append(formatLine("Pajak", formatCurrency(receipt.tax), maxChars))
                append("\n")
            }

            // Total (bold)
            append(ESC_BOLD_ON)
            append(formatLine("TOTAL", formatCurrency(receipt.total), maxChars))
            append(ESC_BOLD_OFF)
            append("\n")
            append("\n")

            // Payment method
            append("Metode: ${receipt.payment?.method ?: "-"}\n")

            // CASH: show tender and change
            if (receipt.payment?.method?.equals("CASH", ignoreCase = true) == true) {
                receipt.payment.amountReceived?.let { received ->
                    append(formatLine("Diterima", formatCurrency(received), maxChars))
                    append("\n")
                }
                // Show change amount (even if 0)
                if (receipt.payment.changeAmount != null) {
                    append(formatLine("Kembalian", formatCurrency(receipt.payment.changeAmount), maxChars))
                    append("\n")
                }
            }
            // QRIS: no fake tender/change

            append("\n")
            append(ESC_ALIGN_CENTER)
            append("Terima kasih\n")
            append("\n")

            // Feed and cut
            append(ESC_FEED_CUT)
        }.toByteArray(Charsets.UTF_8)
    }

    /**
     * Generate drawer pulse command.
     * ESC p m t1 t2
     * Generic pin 2 pulse: ESC p 0 50 250
     */
    fun formatDrawerPulse(profile: DrawerPulseProfile): ByteArray {
        return when (profile) {
            DrawerPulseProfile.DEFAULT -> byteArrayOf(
                0x1B, 0x70, // ESC p
                0x00, // Pin 2
                0x32, // t1 = 50ms on-time
                0xFA.toByte(), // t2 = 250ms off-time
            )
        }
    }

    private fun formatCurrency(amount: Long): String {
        return currencyFormat.format(amount).replace("Rp", "Rp ")
    }

    private fun formatLine(left: String, right: String, maxChars: Int): String {
        val totalLen = left.length + right.length
        val spacing = if (totalLen < maxChars) {
            " ".repeat(maxChars - totalLen)
        } else {
            " "
        }
        return left + spacing + right
    }

    private fun separator(maxChars: Int): String {
        return "-".repeat(maxChars)
    }

    private fun String.truncate(maxLength: Int): String {
        return if (length <= maxLength) this else take(maxLength - 3) + "..."
    }

    private companion object {
        // ESC/POS commands
        const val ESC_INIT = "\u001B@" // Initialize printer
        const val ESC_ALIGN_LEFT = "\u001Ba\u0000"
        const val ESC_ALIGN_CENTER = "\u001Ba\u0001"
        const val ESC_BOLD_ON = "\u001BE\u0001"
        const val ESC_BOLD_OFF = "\u001BE\u0000"
        const val ESC_FEED_CUT = "\n\n\n\u001Bd\u0005\u001Bm" // Feed 5 lines + partial cut
    }
}
