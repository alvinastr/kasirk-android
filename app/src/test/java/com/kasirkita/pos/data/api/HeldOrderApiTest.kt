package com.kasirkita.pos.data.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.kasirkita.pos.data.model.CreateHeldOrderRequest
import com.kasirkita.pos.data.model.HeldOrderCheckoutResponse
import com.kasirkita.pos.data.model.HeldOrderItemModifierResponse
import com.kasirkita.pos.data.model.HeldOrderItemRequest
import com.kasirkita.pos.data.model.HeldOrderItemResponse
import com.kasirkita.pos.data.model.HeldOrderListResponse
import com.kasirkita.pos.data.model.HeldOrderResponse
import com.kasirkita.pos.data.model.HeldOrderSummaryResponse
import com.kasirkita.pos.data.model.HeldOrdersResponse
import com.kasirkita.pos.data.model.HeldOrdersMetaResponse
import com.kasirkita.pos.data.model.UpdateHeldOrderRequest
import com.kasirkita.pos.data.model.toDomain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type

class HeldOrderApiTest {

    private val gson = Gson()

    @Test
    fun createRequest_serializesContractFields() {
        val request = CreateHeldOrderRequest(
            outletId = OUTLET_ID,
            cashierSessionId = SESSION_ID,
            label = "Label",
            items = listOf(
                HeldOrderItemRequest(
                    productId = PRODUCT_ID,
                    quantity = 2,
                    modifierOptionIds = listOf(MODIFIER_OPTION_ID),
                    note = "Note",
                ),
            ),
        )
        val json = gson.toJson(request)
        val parsed = JsonParser.parseString(json).asJsonObject

        assertTrue(parsed.has("outlet_id"))
        assertTrue(parsed.has("cashier_session_id"))
        assertEquals(OUTLET_ID, parsed["outlet_id"].asString)
        assertEquals(SESSION_ID, parsed["cashier_session_id"].asString)
        assertEquals("Label", parsed["label"].asString)
        val items = parsed.getAsJsonArray("items")
        assertEquals(1, items.size())
        val item = items[0].asJsonObject
        assertEquals(PRODUCT_ID, item["product_id"].asString)
        assertEquals(2, item["quantity"].asInt)
        assertEquals(
            listOf(MODIFIER_OPTION_ID),
            gson.fromJson<List<String>>(item.getAsJsonArray("modifier_option_ids"), object : TypeToken<List<String>>() {}.type),
        )
        assertEquals("Note", item["note"].asString)
    }

    @Test
    fun updateRequest_serializesExpectedVersionAndItems() {
        val request = UpdateHeldOrderRequest(
            expectedVersion = VERSION,
            label = "Updated label",
            items = listOf(
                HeldOrderItemRequest(
                    productId = PRODUCT_ID,
                    quantity = 3,
                    modifierOptionIds = emptyList(),
                ),
            ),
        )
        val json = gson.toJson(request)
        val parsed = JsonParser.parseString(json).asJsonObject

        assertEquals(VERSION, parsed["expected_version"].asInt)
        assertEquals("Updated label", parsed["label"].asString)
        val items = parsed.getAsJsonArray("items")
        assertEquals(1, items.size())
        assertEquals(3, items[0].asJsonObject["quantity"].asInt)
    }

    @Test
    fun summaryResponse_mapsToDomainAndItemCount() {
        val response = heldOrderResponse()
        val domain = response.toDomain()

        assertEquals(HELD_ORDER_ID, domain.id)
        assertEquals(OUTLET_ID, domain.outletId)
        assertEquals(SESSION_ID, domain.cashierSessionId)
        assertEquals(USER_ID, domain.cashierUserId)
        assertEquals("Label", domain.label)
        assertEquals("OPEN", domain.status)
        assertEquals(VERSION, domain.version)
        assertEquals(50_000L, domain.totalEstimate)
        assertEquals(3, domain.itemCount)
    }

    @Test
    fun detailResponse_mapsItemsAndModifiers() {
        val response = heldOrderDetailResponse()
        val domain = response.toDomain()

        val item = domain.items.single()
        assertEquals(HELD_ORDER_ITEM_ID, item.id)
        assertEquals(PRODUCT_ID, item.productId)
        assertEquals(2, item.quantity)
        assertEquals(12_500L, item.basePrice)
        assertEquals(15_500L, item.effectivePrice)
        assertEquals(31_000L, item.lineTotal)
        assertEquals("Note", item.note)
        assertEquals(0, item.displayOrder)

        val modifier = item.modifiers.single()
        assertEquals(HELD_ORDER_MODIFIER_ID, modifier.id)
        assertEquals(MODIFIER_GROUP_ID, modifier.modifierGroupId)
        assertEquals(MODIFIER_OPTION_ID, modifier.modifierOptionId)
        assertEquals("Group", modifier.groupName)
        assertEquals("Option", modifier.optionName)
        assertEquals(3_000L, modifier.priceDelta)
    }

    @Test
    fun checkoutResponse_originalConversion_parsesReplayedFalse() {
        val json = """
            {
              "transaction": ${canonicalTransactionJson()},
              "replayed": false
            }
        """.trimIndent()

        val response = gson.fromJson(json, HeldOrderCheckoutResponse::class.java)

        assertFalse(response.replayed)
        assertEquals(TRANSACTION_ID, response.transaction.transactionId)
        assertEquals(CLIENT_TRANSACTION_ID, response.transaction.clientTransactionId)
        assertEquals(OUTLET_ID, response.transaction.outletId)
        assertEquals(USER_ID, response.transaction.userId)
        assertEquals(SESSION_ID, response.transaction.cashierSessionId)
        assertEquals("COMPLETED", response.transaction.status)
        assertEquals(50_000L, response.transaction.total)
        assertEquals(PRODUCT_ID, response.transaction.items.single().productId)
        assertEquals(MODIFIER_OPTION_ID, response.transaction.items.single().modifiers!!.single().modifierOptionId)
        assertEquals("CASH", response.transaction.payments.single().method)
    }

    @Test
    fun checkoutResponse_replayedConversion_parsesReplayedTrue() {
        val json = """
            {
              "transaction": ${canonicalTransactionJson()},
              "replayed": true
            }
        """.trimIndent()

        val response = gson.fromJson(json, HeldOrderCheckoutResponse::class.java)

        assertTrue(response.replayed)
        assertEquals(TRANSACTION_ID, response.transaction.transactionId)
    }

    @Test
    fun checkoutResponse_mapsToDomainUsingExistingCanonicalMapper() {
        val json = """
            {
              "transaction": ${canonicalTransactionJson()},
              "replayed": false
            }
        """.trimIndent()

        val response = gson.fromJson(json, HeldOrderCheckoutResponse::class.java)
        val domain = response.transaction.toDomain()

        assertEquals(TRANSACTION_ID, domain.id)
        assertEquals(CLIENT_TRANSACTION_ID, domain.clientTransactionId)
        assertEquals(PRODUCT_ID, domain.items.single().productId)
        assertEquals(MODIFIER_OPTION_ID, domain.items.single().modifierSnapshots.single().modifierOptionId)
        assertEquals("CASH", domain.payments.single().method)
    }

    @Test
    fun checkoutResponse_missingReplayed_doesNotSilentlyBecomeFalse() {
        val json = """
            {
              "transaction": ${canonicalTransactionJson()}
            }
        """.trimIndent()

        val parsed = runCatching { gson.fromJson(json, HeldOrderCheckoutResponse::class.java) }

        assertTrue("Missing 'replayed' must fail parsing instead of defaulting to false", parsed.isFailure)
    }

    @Test
    fun checkoutResponse_malformedWrapper_failsParsing() {
        val flatLegacyJson = canonicalTransactionJson()

        val flat = runCatching { gson.fromJson(flatLegacyJson, HeldOrderCheckoutResponse::class.java) }

        assertTrue("A flat canonical transaction must not parse as the checkout envelope", flat.isFailure)

        val wrongTransactionType = """
            {
              "transaction": "not-an-object",
              "replayed": false
            }
        """.trimIndent()

        val nested = runCatching { gson.fromJson(wrongTransactionType, HeldOrderCheckoutResponse::class.java) }

        assertTrue("A non-object transaction must fail parsing", nested.isFailure)
    }

    @Test
    fun listResponse_mapsMetaAndData() {
        val response = HeldOrdersResponse(
            data = listOf(heldOrderSummaryResponse()),
            meta = HeldOrdersMetaResponse(1, 20, 1, 1),
        )

        assertEquals(1, response.data.size)
        assertEquals(1, response.meta.total)
        assertEquals(1, response.meta.totalPages)
    }

    @Test
    fun listSummaryJson_withoutDetailItems_mapsNullableFieldsAndPagination() {
        val json = """
            {
              "data": [
                {
                  "held_order_id": "$HELD_ORDER_ID",
                  "outlet_id": "$OUTLET_ID",
                  "cashier_session_id": "$SESSION_ID",
                  "cashier_user_id": "$USER_ID",
                  "cashier_name": null,
                  "label": null,
                  "status": "OPEN",
                  "version": 1,
                  "subtotal_estimate": 50000,
                  "tax_estimate": 0,
                  "total_estimate": 50000,
                  "item_count": 3,
                  "created_at": "2026-10-07T10:00:00.000Z",
                  "updated_at": "2026-10-07T10:05:00.000Z",
                  "cancelled_at": null,
                  "converted_at": null
                }
              ],
              "meta": {
                "page": 2,
                "limit": 20,
                "total": 21,
                "total_pages": 2
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, HeldOrdersResponse::class.java)
        val order = response.data.single().toDomain()

        assertEquals(2, response.meta.page)
        assertEquals(20, response.meta.limit)
        assertEquals(21, response.meta.total)
        assertEquals(2, response.meta.totalPages)
        assertEquals(HELD_ORDER_ID, order.id)
        assertNull(order.cashierName)
        assertNull(order.label)
        assertNull(order.cancelledAt)
        assertNull(order.convertedAt)
        assertTrue(order.items.isEmpty())
    }

    private companion object {
        const val HELD_ORDER_ID = "held-order-id"
        const val OUTLET_ID = "outlet-id"
        const val SESSION_ID = "session-id"
        const val USER_ID = "user-id"
        const val VERSION = 1
        const val PRODUCT_ID = "product-id"
        const val MODIFIER_OPTION_ID = "modifier-option-id"
        const val MODIFIER_GROUP_ID = "modifier-group-id"
        const val HELD_ORDER_ITEM_ID = "held-order-item-id"
        const val HELD_ORDER_MODIFIER_ID = "held-order-modifier-id"
        const val TRANSACTION_ID = "transaction-id"
        const val CLIENT_TRANSACTION_ID = "client-transaction-id"

        fun canonicalTransactionJson(): String = """
            {
              "transaction_id": "$TRANSACTION_ID",
              "client_transaction_id": "$CLIENT_TRANSACTION_ID",
              "outlet_id": "$OUTLET_ID",
              "user_id": "$USER_ID",
              "customer_id": null,
              "cashier_session_id": "$SESSION_ID",
              "shift_id": null,
              "status": "COMPLETED",
              "subtotal": 50000,
              "discount": 0,
              "tax": 0,
              "total": 50000,
              "items": [
                {
                  "id": "item-id",
                  "product_id": "$PRODUCT_ID",
                  "quantity": 2,
                  "unit_price": 25000,
                  "subtotal": 50000,
                  "modifiers": [
                    {
                      "id": "modifier-id",
                      "modifier_group_id": "$MODIFIER_GROUP_ID",
                      "modifier_option_id": "$MODIFIER_OPTION_ID",
                      "group_name_snapshot": "Group",
                      "option_name_snapshot": "Option",
                      "price_delta_snapshot": 3000
                    }
                  ]
                }
              ],
              "payments": [
                {
                  "id": "payment-id",
                  "method": "CASH",
                  "status": "PAID",
                  "amount": 50000,
                  "amount_received": 60000,
                  "change_amount": 10000,
                  "paid_at": "2026-10-07T10:00:00.000Z"
                }
              ],
              "change": 10000,
              "created_at": "2026-10-07T10:00:00.000Z"
            }
        """.trimIndent()

        fun heldOrderResponse() = HeldOrderResponse(
            heldOrderId = HELD_ORDER_ID,
            outletId = OUTLET_ID,
            cashierSessionId = SESSION_ID,
            cashierUserId = USER_ID,
            cashierName = "Cashier",
            label = "Label",
            status = "OPEN",
            version = VERSION,
            subtotalEstimate = 50_000L,
            taxEstimate = 0L,
            totalEstimate = 50_000L,
            itemCount = 3,
            createdAt = "2026-10-07T10:00:00.000Z",
            updatedAt = "2026-10-07T10:00:00.000Z",
            cancelledAt = null,
            convertedAt = null,
        )

        fun heldOrderSummaryResponse() = HeldOrderSummaryResponse(
            heldOrderId = HELD_ORDER_ID,
            outletId = OUTLET_ID,
            cashierSessionId = SESSION_ID,
            cashierUserId = USER_ID,
            cashierName = "Cashier",
            label = "Label",
            status = "OPEN",
            version = VERSION,
            subtotalEstimate = 50_000L,
            taxEstimate = 0L,
            totalEstimate = 50_000L,
            itemCount = 3,
            createdAt = "2026-10-07T10:00:00.000Z",
            updatedAt = "2026-10-07T10:00:00.000Z",
            cancelledAt = null,
            convertedAt = null,
        )

        fun heldOrderDetailResponse(): HeldOrderResponse = heldOrderResponse().copy(
            actorRole = "CASHIER",
            convertedTransactionId = null,
            items = listOf(
                HeldOrderItemResponse(
                    heldOrderItemId = HELD_ORDER_ITEM_ID,
                    productId = PRODUCT_ID,
                    quantity = 2,
                    productName = "Product",
                    sku = "SKU",
                    basePrice = 12_500L,
                    effectivePrice = 15_500L,
                    lineSubtotal = 31_000L,
                    lineDiscount = 0L,
                    lineTotal = 31_000L,
                    note = "Note",
                    displayOrder = 0,
                    modifiers = listOf(
                        HeldOrderItemModifierResponse(
                            heldOrderItemModifierId = HELD_ORDER_MODIFIER_ID,
                            modifierGroupId = MODIFIER_GROUP_ID,
                            modifierOptionId = MODIFIER_OPTION_ID,
                            groupName = "Group",
                            optionName = "Option",
                            priceDelta = 3_000L,
                        ),
                    ),
                ),
            ),
        )
    }
}
