package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptBlock
import com.kasirkita.pos.domain.model.ReceiptBlockType
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptLine
import com.kasirkita.pos.domain.model.ReceiptLineStyle
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptTextAlignment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class BuildReceiptDocumentUseCase @Inject constructor() {
    operator fun invoke(receipt: Receipt, settings: ReceiptSettings? = null, paperWidthMm: Int): ReceiptDocument {
        val canonicalWidthMm = if (paperWidthMm == 80) 80 else 58
        val width = if (canonicalWidthMm == 80) 48 else 32
        val visibility = settings?.visibility
        val header = settings?.header
        val footer = settings?.footer

        val headerLines = mutableListOf<ReceiptLine>()
        addValue(header?.storeName ?: receipt.tenant.name, headerLines, width, ReceiptTextAlignment.Center)
        addValue(header?.outletName ?: receipt.outlet.name, headerLines, width, ReceiptTextAlignment.Center)
        val address = if (settings == null) header?.address ?: receipt.outlet.address ?: receipt.tenant.address else header?.address
        addValue(address, headerLines, width, ReceiptTextAlignment.Center)
        addValue(header?.phone, headerLines, width, ReceiptTextAlignment.Center, prefix = "Telp: ")
        addValue(header?.additionalText, headerLines, width, ReceiptTextAlignment.Center)
        headerLines += ReceiptLine("", ReceiptTextAlignment.Center)

        val metadata = mutableListOf<ReceiptLine>()
        addValue(receipt.transactionId, metadata, width, prefix = "ID: ")
        addValue(receipt.createdAt.formatReceiptTimestamp(), metadata, width, prefix = "Tanggal: ")
        if (visibility?.showCashier != false) addValue(receipt.cashier.name, metadata, width, prefix = "Kasir: ")
        if (visibility?.showCustomer != false) addValue(receipt.customer?.name, metadata, width, prefix = "Pelanggan: ")
        metadata += separator(width)

        val itemLines = mutableListOf<ReceiptLine>()
        receipt.items.forEach { item ->
            addValue(item.productNameSnapshot ?: item.productName, itemLines, width)
            if (visibility?.showSku != false) addValue(item.skuSnapshot ?: item.sku, itemLines, width, prefix = "  SKU: ")
            if (visibility?.showModifiers != false) item.modifierSnapshots.forEach { modifier ->
                addValue("${modifier.groupName}: ${modifier.optionName}", itemLines, width, prefix = "  + ")
            }
            if (visibility?.showItemNotes != false) addValue(item.note, itemLines, width, prefix = "  Catatan: ")
            addPair("${item.quantity} x ${item.unitPrice.money()}", item.subtotal.money(), itemLines, width)
        }
        itemLines += separator(width)

        val totals = mutableListOf<ReceiptLine>()
        addPair("Subtotal", receipt.subtotal.money(), totals, width)
        if (receipt.discount > 0) addPair("Diskon", "-${receipt.discount.money()}", totals, width)
        if (receipt.tax > 0) addPair("Pajak", receipt.tax.money(), totals, width)
        addPair("TOTAL", receipt.total.money(), totals, width, style = ReceiptLineStyle.Emphasis)

        val payment = mutableListOf<ReceiptLine>()
        payment += ReceiptLine("")
        addValue(receipt.payment?.method ?: "-", payment, width, prefix = "Metode: ")
        if (receipt.payment?.method.equals("CASH", ignoreCase = true)) {
            addPair("Diterima", receipt.payment?.amountReceived?.money(), payment, width)
            addPair("Kembalian", receipt.payment?.changeAmount?.money(), payment, width)
        }

        val footerLines = mutableListOf<ReceiptLine>()
        footerLines += ReceiptLine("")
        addValue(footer?.thankYouText ?: "Terima kasih", footerLines, width, ReceiptTextAlignment.Center)
        addValue(footer?.promoText, footerLines, width, ReceiptTextAlignment.Center)

        return ReceiptDocument(
            paperWidthMm = canonicalWidthMm,
            characterWidth = width,
            blocks = listOf(
                ReceiptBlock(ReceiptBlockType.Header, headerLines),
                ReceiptBlock(ReceiptBlockType.TransactionMetadata, metadata),
                ReceiptBlock(ReceiptBlockType.Items, itemLines),
                ReceiptBlock(ReceiptBlockType.Totals, totals),
                ReceiptBlock(ReceiptBlockType.Payment, payment),
                ReceiptBlock(ReceiptBlockType.Footer, footerLines),
            ),
        )
    }

    private fun separator(width: Int) = ReceiptLine("-".repeat(width), style = ReceiptLineStyle.Separator)

    private fun addValue(
        value: String?,
        target: MutableList<ReceiptLine>,
        width: Int,
        alignment: ReceiptTextAlignment = ReceiptTextAlignment.Left,
        prefix: String = "",
    ) {
        if (value == null) return
        val logicalLines = value.normalizeLogicalLines()
        if (logicalLines.isEmpty()) return
        logicalLines.forEach { logicalLine ->
            if (logicalLine.isEmpty()) {
                target += ReceiptLine("", alignment)
            } else {
                wrap(prefix + logicalLine, width).forEach { target += ReceiptLine(it, alignment) }
            }
        }
    }

    private fun addPair(
        left: String,
        right: String?,
        target: MutableList<ReceiptLine>,
        width: Int,
        style: ReceiptLineStyle = ReceiptLineStyle.Normal,
    ) {
        val safeLeft = left.trim()
        val safeRight = right?.trim().orEmpty()
        if (safeRight.isEmpty()) {
            if (safeLeft.isNotEmpty()) wrap(safeLeft, width).forEach { target += ReceiptLine(it, style = style) }
            return
        }
        if (safeLeft.isEmpty()) {
            wrap(safeRight, width).forEach { target += ReceiptLine(it, ReceiptTextAlignment.Right, style) }
            return
        }
        if (safeLeft.length + 1 + safeRight.length <= width) {
            target += ReceiptLine(safeLeft + " ".repeat(width - safeLeft.length - safeRight.length) + safeRight, style = style)
            return
        }
        wrap(safeLeft, width).forEach { target += ReceiptLine(it, style = style) }
        wrap(safeRight, width).forEach { target += ReceiptLine(it, ReceiptTextAlignment.Right, style) }
    }

    private fun String.normalizeLogicalLines(): List<String> {
        val cleaned = buildString(length) {
            var index = 0
            while (index < this@normalizeLogicalLines.length) {
                val character = this@normalizeLogicalLines[index]
                when {
                    character == '\r' -> {
                        if (index + 1 < this@normalizeLogicalLines.length && this@normalizeLogicalLines[index + 1] == '\n') index++
                        append('\n')
                    }
                    character == '\n' -> append('\n')
                    character == '\t' -> append(' ')
                    character.isUnsafeControl() -> Unit
                    character.isWhitespace() -> append(' ')
                    else -> append(character)
                }
                index++
            }
        }
        val lines = cleaned.split('\n').map { it.trim() }
        return if (lines.all { it.isEmpty() }) emptyList() else lines
    }

    private fun wrap(value: String, width: Int): List<String> {
        if (value.length <= width) return listOf(value)
        val result = mutableListOf<String>()
        val tokens = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return listOf("")
        var current = ""
        tokens.forEach { token ->
            val withSpace = if (current.isEmpty()) token else "$current $token"
            if (withSpace.length > width) {
                if (current.isNotEmpty()) result += current
                var remaining = token
                while (remaining.length > width) {
                    result += remaining.take(width)
                    remaining = remaining.drop(width)
                }
                current = remaining
            } else {
                current = withSpace
            }
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private fun Char.isUnsafeControl(): Boolean = this == '\u007F' || code < 0x20 || code in 0x80..0x9F

    private fun Long.money(): String {
        val raw = toString()
        val negative = raw.startsWith("-")
        val digits = raw.filter { it.isDigit() }.ifEmpty { "0" }
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (negative) "-Rp $grouped" else "Rp $grouped"
    }

    private fun String?.formatReceiptTimestamp(): String {
        val formatted = this?.let { raw ->
            runCatching {
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)
                    .withZone(ZoneId.of("Asia/Jakarta"))
                    .format(Instant.parse(raw))
            }.getOrNull()
        }
        return formatted?.takeIf { it.isNotBlank() } ?: "-"
    }
}
