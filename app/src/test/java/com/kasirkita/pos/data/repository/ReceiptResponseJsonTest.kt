package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.ReceiptResponse
import com.kasirkita.pos.data.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptResponseJsonTest {

    private val gson = Gson()

    @Test
    fun receiptCashPayment_mapsAmountReceivedChangeAmountAndTopLevelChange() {
        val response = gson.fromJson(RECEIPT_CASH_JSON, ReceiptResponse::class.java)
        val receipt = response.toDomain()

        assertNotNull(receipt.payment)
        assertEquals("CASH", receipt.payment?.method)
        assertEquals(25_000L, receipt.payment?.amount)
        assertEquals(30_000L, receipt.payment?.amountReceived)
        assertEquals(5_000L, receipt.payment?.changeAmount)
        assertEquals(5_000L, receipt.change)
    }

    @Test
    fun receiptQrisPayment_hasNullAmountReceivedAndChangeAmount() {
        val response = gson.fromJson(RECEIPT_QRIS_JSON, ReceiptResponse::class.java)
        val receipt = response.toDomain()

        assertNotNull(receipt.payment)
        assertEquals("QRIS", receipt.payment?.method)
        assertEquals(25_000L, receipt.payment?.amount)
        assertNull(receipt.payment?.amountReceived)
        assertNull(receipt.payment?.changeAmount)
        assertNull(receipt.change)
    }

    @Test
    fun receiptItem_mapsSnapshotFieldsSeparatelyFromDisplayFields() {
        val response = gson.fromJson(RECEIPT_SNAPSHOT_JSON, ReceiptResponse::class.java)
        val receipt = response.toDomain()
        val item = receipt.items.single()

        assertEquals("Kopi Susu", item.productName)
        assertEquals("KOPI", item.sku)
        assertEquals("Kopi Susu", item.productNameSnapshot)
        assertEquals("KOPI", item.skuSnapshot)
        assertEquals(12_000L, item.basePriceSnapshot)
        assertEquals(12_500L, item.effectivePriceSnapshot)
        assertEquals("less ice", item.note)
    }

    @Test
    fun receiptItem_mapsModifierSnapshots() {
        val response = gson.fromJson(RECEIPT_MODIFIER_JSON, ReceiptResponse::class.java)
        val receipt = response.toDomain()
        val item = receipt.items.single()
        val modifier = item.modifierSnapshots.single()

        assertEquals("modifier-id", modifier.id)
        assertEquals("group-id", modifier.modifierGroupId)
        assertEquals("option-id", modifier.modifierOptionId)
        assertEquals("Size", modifier.groupName)
        assertEquals("Large", modifier.optionName)
        assertEquals(500L, modifier.priceDelta)
    }

    private companion object {
        val RECEIPT_CASH_JSON = """
            {
              "transaction_id": "tx-id",
              "client_transaction_id": "client-id",
              "status": "COMPLETED",
              "created_at": "2026-09-23T10:00:00.000Z",
              "store": {"id": "store-id", "name": "Store", "address": null},
              "outlet": {"id": "outlet-id", "name": "Outlet", "address": null},
              "cashier": {"id": "cashier-id", "name": "Cashier"},
              "customer": null,
              "items": [{
                "item_id": "item-id",
                "product_id": "product-id",
                "product_name": "Coffee",
                "sku": "COF",
                "quantity": 1,
                "unit_price": 25000,
                "subtotal": 25000
              }],
              "payment": {
                "id": "payment-id",
                "method": "CASH",
                "status": "PAID",
                "amount": 25000,
                "amount_received": 30000,
                "change_amount": 5000,
                "provider": null,
                "provider_reference": null,
                "paid_at": "2026-09-23T10:00:00.000Z"
              },
              "totals": {"subtotal": 25000, "discount": 0, "tax": 0, "total": 25000},
              "change": 5000
            }
        """.trimIndent()

        val RECEIPT_QRIS_JSON = """
            {
              "transaction_id": "tx-id",
              "client_transaction_id": "client-id",
              "status": "COMPLETED",
              "created_at": "2026-09-23T10:00:00.000Z",
              "store": {"id": "store-id", "name": "Store", "address": null},
              "outlet": {"id": "outlet-id", "name": "Outlet", "address": null},
              "cashier": {"id": "cashier-id", "name": "Cashier"},
              "customer": null,
              "items": [{
                "item_id": "item-id",
                "product_id": "product-id",
                "product_name": "Coffee",
                "sku": "COF",
                "quantity": 1,
                "unit_price": 25000,
                "subtotal": 25000
              }],
              "payment": {
                "id": "payment-id",
                "method": "QRIS",
                "status": "PAID",
                "amount": 25000,
                "amount_received": null,
                "change_amount": null,
                "provider": "gopay",
                "provider_reference": "gopay-ref",
                "paid_at": "2026-09-23T10:00:00.000Z"
              },
              "totals": {"subtotal": 25000, "discount": 0, "tax": 0, "total": 25000},
              "change": null
            }
        """.trimIndent()

        val RECEIPT_SNAPSHOT_JSON = """
            {
              "transaction_id": "tx-id",
              "client_transaction_id": "client-id",
              "status": "COMPLETED",
              "created_at": "2026-09-23T10:00:00.000Z",
              "store": {"id": "store-id", "name": "Store", "address": null},
              "outlet": {"id": "outlet-id", "name": "Outlet", "address": null},
              "cashier": {"id": "cashier-id", "name": "Cashier"},
              "customer": null,
              "items": [{
                "item_id": "item-id",
                "product_id": "product-id",
                "product_name": "Kopi Susu",
                "sku": "KOPI",
                "quantity": 2,
                "unit_price": 12500,
                "subtotal": 25000,
                "base_price": 12000,
                "effective_price": 12500,
                "note": "less ice"
              }],
              "payment": {
                "id": "payment-id",
                "method": "CASH",
                "status": "PAID",
                "amount": 25000,
                "amount_received": 30000,
                "change_amount": 5000,
                "provider": null,
                "provider_reference": null,
                "paid_at": "2026-09-23T10:00:00.000Z"
              },
              "totals": {"subtotal": 25000, "discount": 0, "tax": 0, "total": 25000},
              "change": 5000
            }
        """.trimIndent()

        val RECEIPT_LEGACY_JSON = """
            {
              "transaction_id": "tx-id",
              "client_transaction_id": "client-id",
              "status": "COMPLETED",
              "created_at": "2026-09-23T10:00:00.000Z",
              "store": {"id": "store-id", "name": "Store", "address": null},
              "outlet": {"id": "outlet-id", "name": "Outlet", "address": null},
              "cashier": {"id": "cashier-id", "name": "Cashier"},
              "customer": null,
              "items": [{
                "item_id": "item-id",
                "product_id": "product-id",
                "product_name": "Legacy Display",
                "sku": "LEGACY",
                "quantity": 1,
                "unit_price": 25000,
                "subtotal": 25000,
                "base_price": 25000,
                "effective_price": 25000,
                "note": null
              }],
              "payment": {
                "id": "payment-id",
                "method": "CASH",
                "status": "PAID",
                "amount": 25000,
                "amount_received": null,
                "change_amount": null,
                "provider": null,
                "provider_reference": null,
                "paid_at": "2026-09-23T10:00:00.000Z"
              },
              "totals": {"subtotal": 25000, "discount": 0, "tax": 0, "total": 25000},
              "change": 0
            }
        """.trimIndent()

        val RECEIPT_MODIFIER_JSON = """
            {
              "transaction_id": "tx-id",
              "client_transaction_id": "client-id",
              "status": "COMPLETED",
              "created_at": "2026-09-23T10:00:00.000Z",
              "store": {"id": "store-id", "name": "Store", "address": null},
              "outlet": {"id": "outlet-id", "name": "Outlet", "address": null},
              "cashier": {"id": "cashier-id", "name": "Cashier"},
              "customer": null,
              "items": [{
                "item_id": "item-id",
                "product_id": "product-id",
                "product_name": "Coffee",
                "sku": "COF",
                "quantity": 1,
                "unit_price": 12500,
                "subtotal": 12500,
                "modifiers": [{
                  "id": "modifier-id",
                  "modifier_group_id": "group-id",
                  "modifier_option_id": "option-id",
                  "group_name": "Size",
                  "option_name": "Large",
                  "price_delta": 500
                }]
              }],
              "payment": {
                "id": "payment-id",
                "method": "CASH",
                "status": "PAID",
                "amount": 12500,
                "amount_received": 15000,
                "change_amount": 2500,
                "provider": null,
                "provider_reference": null,
                "paid_at": "2026-09-23T10:00:00.000Z"
              },
              "totals": {"subtotal": 12500, "discount": 0, "tax": 0, "total": 12500},
              "change": 2500
            }
        """.trimIndent()
    }
}
