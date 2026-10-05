package com.kasirkita.pos.data.printer

import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptItem
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.Payment
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class EscPosReceiptFormatterTest {

    private lateinit var formatter: EscPosReceiptFormatter

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
