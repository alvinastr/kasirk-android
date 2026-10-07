package com.kasirkita.pos.data.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.kasirkita.pos.data.model.CreateHeldOrderRequest
import com.kasirkita.pos.data.model.HeldOrderItemModifierResponse
import com.kasirkita.pos.data.model.HeldOrderItemRequest
import com.kasirkita.pos.data.model.HeldOrderItemResponse
import com.kasirkita.pos.data.model.HeldOrderListResponse
import com.kasirkita.pos.data.model.HeldOrderResponse
import com.kasirkita.pos.data.model.HeldOrdersResponse
import com.kasirkita.pos.data.model.HeldOrdersMetaResponse
import com.kasirkita.pos.data.model.UpdateHeldOrderRequest
import com.kasirkita.pos.data.model.toDomain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun listResponse_mapsMetaAndData() {
        val response = HeldOrdersResponse(
            data = listOf(heldOrderResponse()),
            meta = HeldOrdersMetaResponse(1, 20, 1, 1),
        )

        assertEquals(1, response.data.size)
        assertEquals(1, response.meta.total)
        assertEquals(1, response.meta.totalPages)
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
