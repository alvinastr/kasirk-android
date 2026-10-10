package com.kasirkita.pos.data.printer

import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptItem
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.ReceiptBlock
import com.kasirkita.pos.domain.model.ReceiptBlockType
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptLine
import com.kasirkita.pos.domain.model.ReceiptLineStyle
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptTextAlignment
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import com.kasirkita.pos.domain.usecase.BuildReceiptDocumentUseCase
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class EscPosReceiptFormatterTest {

    private lateinit var formatter: EscPosReceiptFormatter
    private val documentBuilder = BuildReceiptDocumentUseCase()

    @Before
    fun setUp() {
        formatter = EscPosReceiptFormatter()
    }

    private fun createTestReceipt(
        paymentMethod: String = "CASH",
        amountReceived: Long? = 100000,
        changeAmount: Long? = 10000,
    ): Receipt {
        return Receipt(
            transactionId = "TXN-001",
            clientTransactionId = "CLIENT-001",
            status = "success",
            createdAt = "2026-10-05T15:14:00Z",
            tenant = ReceiptTenant("T1", "TestCo", "Jl. Test 123"),
            outlet = ReceiptOutlet("O1", "Outlet 1", "Jl. Test 123"),
            cashier = ReceiptCashier("C1", "Tuti"),
            customer = ReceiptCustomer("CUST1", "Budi", "+6281234567890", null),
            items = listOf(
                ReceiptItem(
                    id = "I1",
                    productId = "P1",
                    productName = "Kopi Hitam",
                    sku = "SKU001",
                    quantity = 2,
                    unitPrice = 15000,
                    subtotal = 30000,
                    productNameSnapshot = "Kopi Hitam",
                    skuSnapshot = "SKU001",
                    basePriceSnapshot = 15000,
                    effectivePriceSnapshot = 15000,
                    note = "Hangat",
                    modifierSnapshots = emptyList(),
                ),
            ),
            payment = Payment(
                id = "PAY-001",
                method = paymentMethod,
                status = "SUCCESS",
                amount = 40000,
                paidAt = "2026-10-05T15:14:05Z",
                amountReceived = amountReceived,
                changeAmount = changeAmount,
            ),
            subtotal = 30000,
            discount = 0,
            tax = 10000,
            total = 40000,
            change = changeAmount,
        )
    }

    @Test
    fun testFormat58mm() {
        val data = formatter.formatReceipt(createTestReceipt(), 58)

        assertTrue(data.isNotEmpty())
        val text = String(data, Charsets.UTF_8)
        assertTrue(text.length > 0)
    }

    @Test
    fun testFormat80mm() {
        val data = formatter.formatReceipt(createTestReceipt(), 80)

        assertTrue(data.isNotEmpty())
        val text = String(data, Charsets.UTF_8)
        assertTrue(text.length > 0)
    }

    @Test
    fun testReceiptContainsTenantName() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains(receipt.tenant.name))
    }

    @Test
    fun testReceiptContainsOutletName() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains(receipt.outlet.name))
    }

    @Test
    fun testReceiptContainsTransactionId() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains(receipt.transactionId))
    }

    @Test
    fun testReceiptContainsCashierName() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains(receipt.cashier.name))
    }

    @Test
    fun testReceiptContainsCustomerName() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains(receipt.customer!!.name))
    }

    @Test
    fun testReceiptContainsProductName() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains("Kopi Hitam"))
    }

    @Test
    fun testReceiptContainsSKU() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains("SKU001"))
    }

    @Test
    fun testReceiptContainsTotals() {
        val receipt = createTestReceipt()
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains("Subtotal"))
        assertTrue(text.contains("TOTAL"))
    }

    @Test
    fun zeroModifierReceiptHasNoSentinelAndKeepsItemSeparatorBeforeSubtotal() {
        val receipt = createTestReceipt().copy(
            items = listOf(
                ReceiptItem(
                    id = "I0",
                    productId = "P0",
                    productName = "Normal Product",
                    sku = "NORMAL-1",
                    quantity = 1,
                    unitPrice = 12_000,
                    subtotal = 12_000,
                    productNameSnapshot = "Normal Product",
                    skuSnapshot = "NORMAL-1",
                    modifierSnapshots = emptyList(),
                ),
            ),
            subtotal = 12_000,
            total = 22_000,
        )
        val document = documentBuilder(receipt, null, 58)
        val itemBlock = document.blocks.first { it.type == ReceiptBlockType.Items }
        val separator = "-".repeat(document.characterWidth)
        assertEquals(1, itemBlock.lines.count { it.text == separator })
        assertEquals("Normal Product", itemBlock.lines.first().text)
        assertTrue(document.blocks.first { it.type == ReceiptBlockType.Totals }.lines.first().text.startsWith("Subtotal"))

        val printable = printableBody(formatter.formatReceipt(document))
        assertFalse(printable.contains("##PLMC"))
        assertFalse(printable.contains("PLMC"))
        assertFalse(printable.contains("modifier" , ignoreCase = true))
        assertTrue(printable.indexOf(separator) < printable.indexOf("Subtotal"))
    }

    @Test
    fun footerUsesOnlyTwoManualTearLineFeedsAndNoCut() {
        val payload = formatter.formatReceipt(documentBuilder(createTestReceipt(), null, 58))
        val footer = "Terima kasih".toByteArray()
        val footerIndex = payload.indexOfSubArray(footer)
        val tail = payload.copyOfRange(footerIndex + footer.size, payload.size)
        assertArrayEquals("\n\u001BE\u0000\u001Ba\u0000\n\n".toByteArray(), tail)
        assertFalse(tail.contains(0x1B.toByte()).and(tail.contains('m'.code.toByte())))
    }

    @Test
    fun testCashPaymentShowsTender() {
        val receipt = createTestReceipt(
            paymentMethod = "CASH",
            amountReceived = 100000,
            changeAmount = 10000,
        )
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains("Diterima"))
    }

    @Test
    fun testCashPaymentShowsChange() {
        val receipt = createTestReceipt(
            paymentMethod = "CASH",
            amountReceived = 100000,
            changeAmount = 10000,
        )
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains("Kembalian"))
    }

    @Test
    fun testCashPaymentZeroChange() {
        val receipt = createTestReceipt(
            paymentMethod = "CASH",
            amountReceived = 40000,
            changeAmount = 0,
        )
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        // Should show 0 kembalian
        assertTrue(text.contains("Kembalian"))
    }

    @Test
    fun testQrisPaymentNoFakeTender() {
        val receipt = createTestReceipt(
            paymentMethod = "QRIS",
            amountReceived = null,
            changeAmount = null,
        )
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertFalse(text.contains("Diterima"))
        assertFalse(text.contains("Kembalian"))
    }

    @Test
    fun testEdcPaymentNoFakeTender() {
        val receipt = createTestReceipt(
            paymentMethod = "EDC",
            amountReceived = null,
            changeAmount = null,
        )
        val data = formatter.formatReceipt(receipt, 58)
        val text = String(data, Charsets.UTF_8)

        assertTrue(text.contains("Metode: EDC"))
        assertFalse(text.contains("Diterima"))
        assertFalse(text.contains("Kembalian"))
    }

    @Test
    fun formatterConsumesCanonicalDocumentAndMapsStyleCommands() {
        val document = ReceiptDocument(
            paperWidthMm = 58,
            characterWidth = 32,
            blocks = listOf(
                ReceiptBlock(
                    ReceiptBlockType.Header,
                    listOf(
                        ReceiptLine("Canonical Header", ReceiptTextAlignment.Center),
                        ReceiptLine("Canonical total", ReceiptTextAlignment.Right, ReceiptLineStyle.Emphasis),
                    ),
                ),
            ),
        )

        val bytes = formatter.formatReceipt(document)
        val text = String(bytes, Charsets.UTF_8)

        assertTrue(text.startsWith("\u001B@"))
        assertTrue(text.contains("\u001Ba\u0001Canonical Header\n"))
        assertTrue(text.contains("\u001Ba\u0002\u001BE\u0001Canonical total\u001BE\u0000\n"))
        assertTrue(text.endsWith("\u001BE\u0000\u001Ba\u0000\n\n"))
    }

    @Test
    fun settingsAwareReceiptPathUsesConfiguredContent() {
        val settings = ReceiptSettings(
            tenantId = "T1",
            outletId = "O1",
            header = ReceiptHeaderSettings("Configured Store", "Configured Outlet", null, null, null),
            visibility = ReceiptVisibilitySettings(false, false, false, false, false),
            footer = ReceiptFooterSettings("Configured thanks", "Configured promo"),
            templateVersion = 1,
            createdAt = null,
            updatedAt = null,
        )

        val text = String(formatter.formatReceipt(createTestReceipt(), settings, 58), Charsets.UTF_8)

        assertTrue(text.contains("Configured Store"))
        assertTrue(text.contains("Configured thanks"))
        assertFalse(text.contains("SKU001"))
        assertFalse(text.contains("Kasir: Tuti"))
    }

    @Test
    fun legacyPathUsesStoredCreatedAtAndPreservesHardwareCommands() {
        val text = String(formatter.formatReceipt(createTestReceipt(), 58), Charsets.UTF_8)

        assertTrue(text.startsWith("\u001B@"))
        assertTrue(text.contains("Tanggal: 05/10/2026 22:14"))
        assertTrue(text.endsWith("\u001BE\u0000\u001Ba\u0000\n\n"))
    }

    @Test
    fun manuallyConstructedDocumentSanitizesUnsafeControlsWithoutDroppingLegitimateText() {
        listOf('\u0000', '\u0009', '\u000A', '\u000D', '\u001B', '\u001D', '\u007F', '\u0085').forEach { control ->
            val document = canonicalDocument(ReceiptLine("unsafe${control}legitimate"))

            val payload = String(formatter.formatReceipt(document), Charsets.UTF_8)

            assertTrue(payload.contains("unsafelegitimate"))
            assertFalse(payload.contains("unsafe${control}legitimate"))
        }
    }

    @Test
    fun configuredAdditionalHeaderAndPromoReachCanonicalEscPosPayload() {
        val settings = ReceiptSettings(
            tenantId = "T1",
            outletId = "O1",
            header = ReceiptHeaderSettings(
                "Coffee & Shop Toga",
                "Outlet 1",
                "Jl. Perjuangan No.4",
                null,
                "Instagram : kopitoga_bekasi\nWifi Password: arabicatoraja",
            ),
            visibility = ReceiptVisibilitySettings(true, true, true, true, true),
            footer = ReceiptFooterSettings("Terima kasih", "Promo akhir pekan"),
            templateVersion = 1,
            createdAt = null,
            updatedAt = null,
        )
        val document = documentBuilder(createTestReceipt(), settings, 58)
        val printable = printableBody(formatter.formatReceipt(document))

        assertTrue(printable.contains("Instagram : kopitoga_bekasi"))
        assertTrue(printable.contains("Wifi Password: arabicatoraja"))
        assertTrue(printable.contains("Promo akhir pekan"))
        assertEquals(document.lines.joinToString("") { "${it.text}\n" }, printable)
    }

    @Test
    fun everyCanonicalTextLineIsEncodedInTheSameOrderIncludingBlankLines() {
        val document = documentBuilder(
            createTestReceipt(),
            ReceiptSettings(
                tenantId = "T1",
                outletId = "O1",
                header = ReceiptHeaderSettings("Store", "Outlet", "Address", null, "Header one\n\nHeader two"),
                visibility = ReceiptVisibilitySettings(true, true, true, true, true),
                footer = ReceiptFooterSettings("Thanks", "Footer one\n\nFooter two"),
                templateVersion = 1,
                createdAt = null,
                updatedAt = null,
            ),
            80,
        )

        assertEquals(
            document.lines.joinToString("") { "${it.text}\n" },
            printableBody(formatter.formatReceipt(document)),
        )
    }

    @Test
    fun canonicalHeaderFooterAlignmentAndTotalEmphasisBecomePrinterCommands() {
        val document = documentBuilder(createTestReceipt(), null, 58)
        val payload = String(formatter.formatReceipt(document), Charsets.UTF_8)
        val total = document.lines.first { it.text.startsWith("TOTAL") }

        document.blocks.first { it.type == ReceiptBlockType.Header }.lines.forEach { line ->
            assertTrue(payload.contains("\u001Ba\u0001${line.text}\n"))
        }
        document.blocks.first { it.type == ReceiptBlockType.Footer }.lines.forEach { line ->
            assertTrue(payload.contains("\u001Ba\u0001${line.text}\n"))
        }
        assertTrue(payload.contains("\u001BE\u0001${total.text}\u001BE\u0000\n"))
    }

    @Test
    fun dataSourcedPrinterCommandSequencesAreRemovedBeforeByteRendering() {
        val injected = "Injected\u001B@\u001D\u0000\u007F\u0085Text"
        val receipt = createTestReceipt(paymentMethod = injected).copy(
            tenant = ReceiptTenant("T1", injected, injected),
            outlet = ReceiptOutlet("O1", injected, injected),
            cashier = ReceiptCashier("C1", injected),
            customer = ReceiptCustomer("CUST1", injected, null, null),
        )

        val text = String(formatter.formatReceipt(receipt, 58), Charsets.UTF_8)

        assertFalse(text.contains("Injected\u001B@"))
        assertFalse(text.contains("Injected\u001D"))
        assertFalse(text.contains("Injected\u0000"))
        assertFalse(text.contains("Injected\u007F"))
        assertFalse(text.contains("Injected\u0085"))
        assertTrue(text.contains("Injected@Text"))
    }

    @Test
    fun emphasisFollowedByNormalResetsBoldAndAlignment() {
        val text = renderLines(
            ReceiptLine("bold", ReceiptTextAlignment.Right, ReceiptLineStyle.Emphasis),
            ReceiptLine("normal", ReceiptTextAlignment.Left),
        )

        assertTrue(text.contains("\u001Ba\u0002\u001BE\u0001bold\u001BE\u0000\n\u001Ba\u0000normal\n"))
    }

    @Test
    fun normalFollowedByEmphasisSetsAndResetsBold() {
        val text = renderLines(
            ReceiptLine("normal", ReceiptTextAlignment.Left),
            ReceiptLine("bold", ReceiptTextAlignment.Center, ReceiptLineStyle.Emphasis),
        )

        assertTrue(text.contains("\u001Ba\u0000normal\n\u001Ba\u0001\u001BE\u0001bold\u001BE\u0000\n"))
    }

    @Test
    fun separatorFollowedByEmphasisDoesNotLeakState() {
        val text = renderLines(
            ReceiptLine("---", ReceiptTextAlignment.Left, ReceiptLineStyle.Separator),
            ReceiptLine("bold", ReceiptTextAlignment.Right, ReceiptLineStyle.Emphasis),
        )

        assertTrue(text.contains("\u001Ba\u0000---\n\u001Ba\u0002\u001BE\u0001bold\u001BE\u0000\n"))
    }

    @Test
    fun blankFollowedByEmphasisDoesNotLeakState() {
        val text = renderLines(
            ReceiptLine("", ReceiptTextAlignment.Center),
            ReceiptLine("bold", ReceiptTextAlignment.Left, ReceiptLineStyle.Emphasis),
        )

        assertTrue(text.contains("\u001Ba\u0001\n\u001Ba\u0000\u001BE\u0001bold\u001BE\u0000\n"))
    }

    private fun canonicalDocument(vararg lines: ReceiptLine) = ReceiptDocument(
        paperWidthMm = 58,
        characterWidth = 32,
        blocks = listOf(ReceiptBlock(ReceiptBlockType.Header, lines.toList())),
    )

    private fun renderLines(vararg lines: ReceiptLine): String =
        String(formatter.formatReceipt(canonicalDocument(*lines)), Charsets.UTF_8)

    private fun printableBody(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)
        .removePrefix("\u001B@")
        .removeSuffix("\u001BE\u0000\u001Ba\u0000\n\n")
        .replace(Regex("\u001Ba[\u0000-\u0002]"), "")
        .replace("\u001BE\u0001", "")
        .replace("\u001BE\u0000", "")

    private fun ByteArray.indexOfSubArray(needle: ByteArray): Int =
        indices.firstOrNull { start -> start + needle.size <= size && needle.indices.all { this[start + it] == needle[it] } } ?: -1

    private fun EscPosReceiptFormatter.formatReceipt(receipt: Receipt, paperWidthMm: Int): ByteArray =
        formatReceipt(documentBuilder(receipt, settings = null, paperWidthMm = paperWidthMm))

    private fun EscPosReceiptFormatter.formatReceipt(
        receipt: Receipt,
        settings: ReceiptSettings?,
        paperWidthMm: Int,
    ): ByteArray = formatReceipt(documentBuilder(receipt, settings, paperWidthMm))

    @Test
    fun testDrawerPulseDefault() {
        val pulseBytes = formatter.formatDrawerPulse(DrawerPulseProfile.DEFAULT)
        assertEquals(5, pulseBytes.size)
        assertEquals(0x1B.toByte(), pulseBytes[0])
        assertEquals(0x70.toByte(), pulseBytes[1])
        assertEquals(0x00.toByte(), pulseBytes[2])
        assertEquals(0x32.toByte(), pulseBytes[3])
        assertEquals(0xFA.toByte(), pulseBytes[4])
    }
}
