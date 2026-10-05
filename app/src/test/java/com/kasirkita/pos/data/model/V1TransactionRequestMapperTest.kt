package com.kasirkita.pos.data.model

import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.CartLineKey
import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.V1Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V1TransactionRequestMapperTest {

    @Test
    fun buildV1TransactionRequest_noModifiersNoNote_mapsCorrectly() {
        val item = CartItem(
            lineKey = CartLineKey.from("product-1", emptyList(), null),
            productId = "product-1",
            name = "Americano",
            sku = "AMR",
            basePrice = 20_000L,
            quantity = 2,
        )

        val request = buildV1TransactionRequest(
            clientTransactionId = "txn-123",
            outletId = "outlet-1",
            cashierSessionId = "session-1",
            items = listOf(item),
            payment = V1Payment(method = "CASH", amountReceived = 50_000L),
        )

        assertEquals("txn-123", request.clientTransactionId)
        assertEquals("outlet-1", request.outletId)
        assertEquals("session-1", request.cashierSessionId)
        assertNull(request.customerId)
        assertNull(request.discount)
        assertEquals(1, request.items.size)

        val mappedItem = request.items[0]
        assertEquals("product-1", mappedItem.productId)
        assertEquals(2, mappedItem.quantity)
        assertEquals(emptyList<String>(), mappedItem.modifierOptionIds)
        assertNull(mappedItem.note)

        assertEquals("CASH", request.payment.method)
        assertEquals(50_000L, request.payment.amountReceived)
    }

    @Test
    fun buildV1TransactionRequest_withModifiersAndNote_preservesAll() {
        val item = CartItem(
            lineKey = CartLineKey.from("product-1", listOf("ice", "shot"), "Sedikit es"),
            productId = "product-1",
            name = "Americano",
            sku = "AMR",
            basePrice = 20_000L,
            quantity = 1,
            modifierSelections = listOf(
                CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L),
                CartModifierSelectionSnapshot("shot", "addons", "Add-ons", "Extra Shot", 5_000L),
            ),
            note = "Sedikit es",
        )

        val request = buildV1TransactionRequest(
            clientTransactionId = "txn-456",
            outletId = "outlet-2",
            cashierSessionId = "session-2",
            items = listOf(item),
            payment = V1Payment(method = "CASH", amountReceived = 30_000L),
            customerId = "customer-1",
            discount = 1_000L,
        )

        assertEquals("txn-456", request.clientTransactionId)
        assertEquals("outlet-2", request.outletId)
        assertEquals("session-2", request.cashierSessionId)
        assertEquals("customer-1", request.customerId)
        assertEquals(1_000L, request.discount)

        val mappedItem = request.items[0]
        assertEquals("product-1", mappedItem.productId)
        assertEquals(1, mappedItem.quantity)
        assertEquals(listOf("ice", "shot"), mappedItem.modifierOptionIds)
        assertEquals("Sedikit es", mappedItem.note)
    }

    @Test
    fun buildV1TransactionRequest_multipleItems_mapsAllCorrectly() {
        val item1 = CartItem(
            lineKey = CartLineKey.from("product-1", emptyList(), null),
            productId = "product-1",
            name = "Americano",
            sku = "AMR",
            basePrice = 20_000L,
            quantity = 2,
        )

        val item2 = CartItem(
            lineKey = CartLineKey.from("product-2", listOf("ice"), "Extra ice"),
            productId = "product-2",
            name = "Latte",
            sku = "LAT",
            basePrice = 25_000L,
            quantity = 1,
            modifierSelections = listOf(
                CartModifierSelectionSnapshot("ice", "temp", "Temperature", "Ice", 0L),
            ),
            note = "Extra ice",
        )

        val request = buildV1TransactionRequest(
            clientTransactionId = "txn-789",
            outletId = "outlet-3",
            cashierSessionId = "session-3",
            items = listOf(item1, item2),
            payment = V1Payment(method = "QRIS", amountReceived = null),
        )

        assertEquals(2, request.items.size)

        val mappedItem1 = request.items[0]
        assertEquals("product-1", mappedItem1.productId)
        assertEquals(2, mappedItem1.quantity)
        assertEquals(emptyList<String>(), mappedItem1.modifierOptionIds)
        assertNull(mappedItem1.note)

        val mappedItem2 = request.items[1]
        assertEquals("product-2", mappedItem2.productId)
        assertEquals(1, mappedItem2.quantity)
        assertEquals(listOf("ice"), mappedItem2.modifierOptionIds)
        assertEquals("Extra ice", mappedItem2.note)

        assertEquals("QRIS", request.payment.method)
        assertNull(request.payment.amountReceived)
    }

    @Test
    fun buildV1TransactionRequest_qrisPayment_noAmountReceived() {
        val item = CartItem(
            lineKey = CartLineKey.from("product-1", emptyList(), null),
            productId = "product-1",
            name = "Americano",
            sku = "AMR",
            basePrice = 20_000L,
            quantity = 1,
        )

        val request = buildV1TransactionRequest(
            clientTransactionId = "txn-qris",
            outletId = "outlet-1",
            cashierSessionId = "session-1",
            items = listOf(item),
            payment = V1Payment(method = "QRIS", amountReceived = null),
        )

        assertEquals("QRIS", request.payment.method)
        assertNull(request.payment.amountReceived)
    }
}
