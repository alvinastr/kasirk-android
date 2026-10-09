package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ModifierSnapshot
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptItem
import com.kasirkita.pos.domain.model.ReceiptLineStyle
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.ReceiptTextAlignment
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildReceiptDocumentUseCaseTest {
    private val builder = BuildReceiptDocumentUseCase()

    @Test
    fun `legacy fallback document for 58 mm is exact`() {
        val document = builder(baseReceipt(), settings = null, paperWidthMm = 58)

        assertEquals(58, document.paperWidthMm)
        assertEquals(32, document.characterWidth)
        assertEquals(
            listOf(
                "Toko Nusantara",
                "Cabang Melati",
                "Jl. Melati 10",
                "",
                "ID: TXN-001",
                "Tanggal: 05/10/2026 22:14",
                "Kasir: Tuti",
                "Pelanggan: Budi",
                "--------------------------------",
                "Kopi Susu Gula Aren",
                "  SKU: KSGA-001",
                "  + Size: Large",
                "  Catatan: Less ice",
                "2 x Rp 18.000          Rp 36.000",
                "Roti Cokelat",
                "  SKU: ROTI-9",
                "1 x Rp 12.000          Rp 12.000",
                "--------------------------------",
                "Subtotal               Rp 48.000",
                "Diskon                 -Rp 3.000",
                "Pajak                   Rp 4.500",
                "TOTAL                  Rp 49.500",
                "",
                "Metode: CASH",
                "Diterima               Rp 50.000",
                "Kembalian                 Rp 500",
                "",
                "Terima kasih",
            ),
            document.textLines(),
        )
        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `legacy fallback document for 80 mm is exact`() {
        val document = builder(baseReceipt(), settings = null, paperWidthMm = 80)

        assertEquals(80, document.paperWidthMm)
        assertEquals(48, document.characterWidth)
        assertEquals(
            listOf(
                "Toko Nusantara",
                "Cabang Melati",
                "Jl. Melati 10",
                "",
                "ID: TXN-001",
                "Tanggal: 05/10/2026 22:14",
                "Kasir: Tuti",
                "Pelanggan: Budi",
                "------------------------------------------------",
                "Kopi Susu Gula Aren",
                "  SKU: KSGA-001",
                "  + Size: Large",
                "  Catatan: Less ice",
                "2 x Rp 18.000                          Rp 36.000",
                "Roti Cokelat",
                "  SKU: ROTI-9",
                "1 x Rp 12.000                          Rp 12.000",
                "------------------------------------------------",
                "Subtotal                               Rp 48.000",
                "Diskon                                 -Rp 3.000",
                "Pajak                                   Rp 4.500",
                "TOTAL                                  Rp 49.500",
                "",
                "Metode: CASH",
                "Diterima                               Rp 50.000",
                "Kembalian                                 Rp 500",
                "",
                "Terima kasih",
            ),
            document.textLines(),
        )
        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `configured header and footer rendering`() {
        val settings = settings(
            storeName = "Warung Kopi Maju Bersama",
            outletName = "Outlet Pasar Baru",
            address = "Jl. Pasar Baru No. 99 Jakarta Pusat",
            phone = "+62 812 3456 7890",
            additionalText = "Buka setiap hari 08.00-22.00",
            thankYouText = "Terima kasih sudah belanja",
            promoText = "Promo Jumat: beli 2 gratis 1",
        )

        val lines = builder(baseReceipt(), settings, 58).textLines()

        assertEquals("Warung Kopi Maju Bersama", lines[0])
        assertEquals("Outlet Pasar Baru", lines[1])
        assertTrue(lines.contains("Jl. Pasar Baru No. 99 Jakarta"))
        assertTrue(lines.contains("Pusat"))
        assertTrue(lines.contains("Telp: +62 812 3456 7890"))
        assertTrue(lines.contains("Buka setiap hari 08.00-22.00"))
        assertTrue(lines.contains("Terima kasih sudah belanja"))
        assertTrue(lines.contains("Promo Jumat: beli 2 gratis 1"))
    }

    @Test
    fun `visibility flags independently hide intended content`() {
        val receipt = baseReceipt()
        val cases = listOf(
            settings(showSku = false) to listOf("SKU:") to listOf("+ Size", "Catatan:", "Kasir:", "Pelanggan:"),
            settings(showModifiers = false) to listOf("+ Size") to listOf("SKU:", "Catatan:", "Kasir:", "Pelanggan:"),
            settings(showItemNotes = false) to listOf("Catatan:") to listOf("SKU:", "+ Size", "Kasir:", "Pelanggan:"),
            settings(showCashier = false) to listOf("Kasir:") to listOf("SKU:", "+ Size", "Catatan:", "Pelanggan:"),
            settings(showCustomer = false) to listOf("Pelanggan:") to listOf("SKU:", "+ Size", "Catatan:", "Kasir:"),
        )

        cases.forEach { (pair, visible) ->
            val (settings, hidden) = pair
            val text = builder(receipt, settings, 58).textLines().joinToString("\n")
            hidden.forEach { assertFalse("hidden $it", text.contains(it)) }
            visible.forEach { assertTrue("visible $it", text.contains(it)) }
        }
    }

    @Test
    fun `transaction createdAt is used rather than current print time`() {
        val text = builder(baseReceipt(createdAt = "2024-01-15T03:04:00Z"), null, 58).textLines().joinToString("\n")

        assertTrue(text.contains("Tanggal: 15/01/2024 10:04"))
        assertFalse(text.contains("2026"))
    }

    @Test
    fun `invalid or blank timestamp uses non date fallback`() {
        listOf("not-a-date", "").forEach { createdAt ->
            val text = builder(baseReceipt(createdAt = createdAt), null, 58).textLines().joinToString("\n")

            assertTrue(text.contains("Tanggal: -"))
            assertFalse(text.contains("1970"))
            assertFalse(text.contains("not-a-date"))
        }
    }

    @Test
    fun `UTC and offset timestamps render deterministically in Asia Jakarta`() {
        val utc = builder(baseReceipt(createdAt = "2024-01-15T03:04:00Z"), null, 58).textLines()
        val offset = builder(baseReceipt(createdAt = "2024-01-15T10:04:00+07:00"), null, 58).textLines()

        assertTrue(utc.contains("Tanggal: 15/01/2024 10:04"))
        assertTrue(offset.contains("Tanggal: 15/01/2024 10:04"))
    }

    @Test
    fun `all dynamic text is normalized before entering canonical lines`() {
        val unsafe = "A\u001B@\u001D\u0000\u007F\u0085B"
        val item = item(productName = unsafe, sku = unsafe, note = unsafe).copy(
            modifierSnapshots = listOf(ModifierSnapshot("M1", "G1", "O1", unsafe, unsafe, 0)),
        )
        val receipt = baseReceipt(items = listOf(item)).copy(
            transactionId = unsafe,
            cashier = ReceiptCashier("C1", unsafe),
            customer = ReceiptCustomer("CUST1", unsafe, null, null),
            payment = payment(unsafe, null, null),
            tenant = ReceiptTenant("T1", unsafe, unsafe),
            outlet = ReceiptOutlet("O1", unsafe, unsafe),
        )
        val configured = settings(
            storeName = unsafe,
            outletName = unsafe,
            address = unsafe,
            phone = unsafe,
            additionalText = unsafe,
            thankYouText = unsafe,
            promoText = unsafe,
        )

        val document = builder(receipt, configured, 58)

        assertTrue(document.lines.any { it.text.contains("A@B") })
        document.lines.forEach { line ->
            assertFalse("unsafe canonical text: ${line.text}", line.text.any(::isForbiddenControl))
        }
        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `multiline and whitespace controls become intentional canonical lines`() {
        val document = builder(
            baseReceipt(),
            settings(
                additionalText = " First\r\nSecond\rThird\n\nFourth\tTabbed ",
                promoText = "Promo one\n\nPromo two",
            ),
            58,
        )
        val lines = document.textLines()

        assertTrue(lines.windowed(5).any { it == listOf("First", "Second", "Third", "", "Fourth Tabbed") })
        assertTrue(lines.windowed(3).any { it == listOf("Promo one", "", "Promo two") })
        assertFalse(lines.any { line -> line.any(::isForbiddenControl) })
    }

    @Test
    fun `long product names wrap without lost characters`() {
        val name = "Produk Sangat Panjang Dengan Banyak Kata Untuk Menguji Pembungkus Baris"
        val document = builder(baseReceipt(items = listOf(item(productName = name, sku = ""))), null, 58)
        val text = document.textLines().joinToString(" ")

        assertTrue(text.contains(name))
        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `long unbroken tokens split safely`() {
        val token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890LONGTOKEN"
        val document = builder(baseReceipt(items = listOf(item(productName = token, sku = ""))), null, 58)
        val text = document.textLines().joinToString("")

        assertTrue(text.contains(token))
        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `long store address and footer text wrap within width`() {
        val document = builder(
            baseReceipt(),
            settings(
                storeName = "Nama Toko Super Panjang Sekali Untuk Cetak Struk",
                address = "Alamat sangat panjang dengan banyak kata dan nomor ruko yang panjang",
                thankYouText = "Terima kasih banyak sudah berbelanja di toko kami hari ini",
                promoText = "PROMOSUPERHEMAT1234567890TANPASPASI",
            ),
            58,
        )

        assertAllTextLinesWithinWidth(document)
        assertTrue(document.textLines().joinToString(" ").contains("Nama Toko Super Panjang Sekali Untuk Cetak Struk"))
    }

    @Test
    fun `long metadata and payment fields wrap within width`() {
        val receipt = baseReceipt().copy(
            transactionId = "TRANSACTION-ID-WITH-A-SINGLE-VERY-LONG-UNBROKEN-TOKEN",
            cashier = ReceiptCashier("C1", "Nama Kasir Sangat Panjang Untuk Menguji Lebar Baris"),
            customer = ReceiptCustomer("CUST1", "Nama Pelanggan Sangat Panjang Untuk Menguji Lebar Baris", null, null),
            payment = payment("TRANSFER-BANK-DENGAN-NAMA-METODE-SANGAT-PANJANG", null, null),
        )

        val document = builder(receipt, null, 58)

        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `pair overflow preserves quantities labels and all monetary characters`() {
        val huge = Long.MAX_VALUE
        val hugeItem = item(
            productName = "Huge",
            sku = "",
            quantity = Int.MAX_VALUE,
            unitPrice = huge,
            subtotal = huge,
            note = null,
            modifiers = emptyList(),
        )
        val receipt = baseReceipt(items = listOf(hugeItem), discount = huge, tax = huge).copy(
            subtotal = huge,
            total = huge,
            payment = payment("CASH", huge, huge),
        )

        val document = builder(receipt, null, 58)
        val compact = document.textLines().joinToString("").replace(" ", "")
        val money = "Rp9.223.372.036.854.775.807"

        assertTrue(compact.contains("${Int.MAX_VALUE}x$money"))
        assertTrue(compact.contains("Subtotal$money"))
        assertTrue(compact.contains("Diskon-$money"))
        assertTrue(compact.contains("Pajak$money"))
        assertTrue(compact.contains("TOTAL$money"))
        assertTrue(compact.contains("Diterima$money"))
        assertTrue(compact.contains("Kembalian$money"))
        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `negative and zero amounts retain sign and digits`() {
        val receipt = baseReceipt(items = listOf(item(quantity = -2, unitPrice = -1, subtotal = -2))).copy(
            subtotal = -2,
            total = 0,
            payment = payment("CASH", 0, -2),
        )
        val compact = builder(receipt, null, 58).textLines().joinToString("").replace(" ", "")

        assertTrue(compact.contains("-2x-Rp1-Rp2"))
        assertTrue(compact.contains("TOTALRp0"))
        assertTrue(compact.contains("DiterimaRp0"))
        assertTrue(compact.contains("Kembalian-Rp2"))
    }

    @Test
    fun `unsupported paper width falls back to canonical 58 mm`() {
        listOf(-1, 0, 57, 59, 81).forEach { requested ->
            val document = builder(baseReceipt(), null, requested)
            assertEquals(58, document.paperWidthMm)
            assertEquals(32, document.characterWidth)
        }
    }

    @Test
    fun `empty configured optional fields add no unintended blank lines`() {
        val baseline = builder(baseReceipt(customer = null), settings(address = null, phone = null, additionalText = null, promoText = null), 58)
        val empty = builder(baseReceipt(customer = null), settings(address = " \t\r\n ", phone = "\u001B", additionalText = "\u0000", promoText = "\u007F"), 58)

        assertEquals(baseline.textLines(), empty.textLines())
    }

    @Test
    fun `discount and tax zero are hidden and non zero are shown`() {
        val zero = builder(baseReceipt(discount = 0, tax = 0), null, 58).textLines().joinToString("\n")
        assertFalse(zero.contains("Diskon"))
        assertFalse(zero.contains("Pajak"))

        val nonZero = builder(baseReceipt(discount = 1_000, tax = 2_000), null, 58).textLines().joinToString("\n")
        assertTrue(nonZero.contains("Diskon"))
        assertTrue(nonZero.contains("Pajak"))
    }

    @Test
    fun `cash tender and change render but non cash payment omits tender and change`() {
        val cash = builder(baseReceipt(payment = payment("CASH", amountReceived = 50_000, changeAmount = 500)), null, 58).textLines().joinToString("\n")
        assertTrue(cash.contains("Diterima"))
        assertTrue(cash.contains("Kembalian"))

        val qris = builder(baseReceipt(payment = payment("QRIS", amountReceived = null, changeAmount = null)), null, 58).textLines().joinToString("\n")
        assertTrue(qris.contains("Metode: QRIS"))
        assertFalse(qris.contains("Diterima"))
        assertFalse(qris.contains("Kembalian"))
    }

    @Test
    fun `null customer and optional fields are safe`() {
        val document = builder(
            baseReceipt(customer = null, outletAddress = null, tenantAddress = "Alamat Tenant"),
            settings(address = null, phone = null, additionalText = null, promoText = null),
            58,
        )
        val text = document.textLines().joinToString("\n")

        assertFalse(text.contains("Pelanggan:"))
        assertFalse(text.contains("Telp:"))
        assertFalse(text.contains("Promo"))
        assertFalse(text.contains("Alamat Tenant"))
        assertAllTextLinesWithinWidth(document)
    }

    @Test
    fun `modifier and note rendering`() {
        val text = builder(baseReceipt(), null, 58).textLines().joinToString("\n")

        assertTrue(text.contains("  + Size: Large"))
        assertTrue(text.contains("  Catatan: Less ice"))
    }

    @Test
    fun `total line is bold and header footer are centered`() {
        val document = builder(baseReceipt(), null, 58)

        assertTrue(document.lines.first().alignment == ReceiptTextAlignment.Center)
        assertTrue(document.lines.last().alignment == ReceiptTextAlignment.Center)
        assertEquals(ReceiptLineStyle.Emphasis, document.lines.first { it.text.startsWith("TOTAL") }.style)
    }

    private fun isForbiddenControl(character: Char): Boolean =
        character == '\u007F' || character.code < 0x20 || character.code in 0x80..0x9F

    private fun assertAllTextLinesWithinWidth(document: com.kasirkita.pos.domain.model.ReceiptDocument) {
        document.textLines().forEach { line ->
            assertTrue("line too long (${line.length}/${document.characterWidth}): $line", line.length <= document.characterWidth)
        }
    }

    private fun com.kasirkita.pos.domain.model.ReceiptDocument.textLines(): List<String> =
        lines.map { it.text }

    private fun settings(
        storeName: String = "Configured Store",
        outletName: String = "Configured Outlet",
        address: String? = "Configured Address",
        phone: String? = null,
        additionalText: String? = null,
        showSku: Boolean = true,
        showModifiers: Boolean = true,
        showItemNotes: Boolean = true,
        showCashier: Boolean = true,
        showCustomer: Boolean = true,
        thankYouText: String = "Thanks configured",
        promoText: String? = null,
    ) = ReceiptSettings(
        tenantId = "T1",
        outletId = "O1",
        header = ReceiptHeaderSettings(storeName, outletName, address, phone, additionalText),
        visibility = ReceiptVisibilitySettings(showSku, showModifiers, showItemNotes, showCashier, showCustomer),
        footer = ReceiptFooterSettings(thankYouText, promoText),
        templateVersion = 1,
        createdAt = null,
        updatedAt = null,
    )

    private fun baseReceipt(
        createdAt: String = "2026-10-05T15:14:00Z",
        items: List<ReceiptItem> = listOf(
            item(),
            item(id = "I2", productName = "Roti Cokelat", sku = "ROTI-9", quantity = 1, unitPrice = 12_000, subtotal = 12_000, note = null, modifiers = emptyList()),
        ),
        discount: Long = 3_000,
        tax: Long = 4_500,
        payment: Payment? = payment("CASH", 50_000, 500),
        customer: ReceiptCustomer? = ReceiptCustomer("CUST1", "Budi", "+6281", null),
        outletAddress: String? = "Jl. Melati 10",
        tenantAddress: String? = "Jl. Tenant 1",
    ) = Receipt(
        transactionId = "TXN-001",
        clientTransactionId = "CLIENT-001",
        status = "completed",
        createdAt = createdAt,
        tenant = ReceiptTenant("T1", "Toko Nusantara", tenantAddress),
        outlet = ReceiptOutlet("O1", "Cabang Melati", outletAddress),
        cashier = ReceiptCashier("C1", "Tuti"),
        customer = customer,
        items = items,
        payment = payment,
        subtotal = items.sumOf { it.subtotal },
        discount = discount,
        tax = tax,
        total = items.sumOf { it.subtotal } - discount + tax,
        change = payment?.changeAmount,
    )

    private fun item(
        id: String = "I1",
        productName: String = "Kopi Susu Gula Aren",
        sku: String = "KSGA-001",
        quantity: Int = 2,
        unitPrice: Long = 18_000,
        subtotal: Long = 36_000,
        note: String? = "Less ice",
        modifiers: List<ModifierSnapshot> = listOf(ModifierSnapshot("M1", "G1", "O1", "Size", "Large", 5_000)),
    ) = ReceiptItem(
        id = id,
        productId = "P$id",
        productName = productName,
        sku = sku,
        quantity = quantity,
        unitPrice = unitPrice,
        subtotal = subtotal,
        productNameSnapshot = productName,
        skuSnapshot = sku,
        basePriceSnapshot = unitPrice,
        effectivePriceSnapshot = unitPrice,
        note = note,
        modifierSnapshots = modifiers,
    )

    private fun payment(method: String, amountReceived: Long?, changeAmount: Long?) = Payment(
        id = "PAY1",
        method = method,
        status = "settled",
        amount = 49_500,
        paidAt = "2026-10-05T15:14:05Z",
        amountReceived = amountReceived,
        changeAmount = changeAmount,
    )
}