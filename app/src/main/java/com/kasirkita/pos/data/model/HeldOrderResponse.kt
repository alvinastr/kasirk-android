package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.HeldOrderItem
import com.kasirkita.pos.domain.model.HeldOrderModifier

data class HeldOrdersResponse(
    val data: List<HeldOrderResponse>,
    val meta: HeldOrdersMetaResponse,
)

data class HeldOrdersMetaResponse(
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerializedName("total_pages")
    val totalPages: Int,
)

typealias HeldOrderListResponse = HeldOrdersResponse

data class HeldOrderResponse(
    @SerializedName("held_order_id")
    val heldOrderId: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("cashier_session_id")
    val cashierSessionId: String,
    @SerializedName("cashier_user_id")
    val cashierUserId: String,
    @SerializedName("cashier_name")
    val cashierName: String?,
    @SerializedName("actor_role")
    val actorRole: String? = null,
    val label: String?,
    val status: String,
    val version: Int,
    @SerializedName("subtotal_estimate")
    val subtotalEstimate: Long,
    @SerializedName("tax_estimate")
    val taxEstimate: Long,
    @SerializedName("total_estimate")
    val totalEstimate: Long,
    @SerializedName("item_count")
    val itemCount: Int,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("cancelled_at")
    val cancelledAt: String?,
    @SerializedName("converted_at")
    val convertedAt: String?,
    @SerializedName("converted_transaction_id")
    val convertedTransactionId: String? = null,
    val items: List<HeldOrderItemResponse> = emptyList(),
)

data class HeldOrderItemResponse(
    @SerializedName("held_order_item_id")
    val heldOrderItemId: String,
    @SerializedName("product_id")
    val productId: String,
    val quantity: Int,
    @SerializedName("product_name")
    val productName: String,
    val sku: String,
    @SerializedName("base_price")
    val basePrice: Long,
    @SerializedName("effective_price")
    val effectivePrice: Long,
    @SerializedName("line_subtotal")
    val lineSubtotal: Long,
    @SerializedName("line_discount")
    val lineDiscount: Long,
    @SerializedName("line_total")
    val lineTotal: Long,
    val note: String?,
    @SerializedName("display_order")
    val displayOrder: Int,
    val modifiers: List<HeldOrderItemModifierResponse> = emptyList(),
)

data class HeldOrderItemModifierResponse(
    @SerializedName("held_order_item_modifier_id")
    val heldOrderItemModifierId: String,
    @SerializedName("modifier_group_id")
    val modifierGroupId: String?,
    @SerializedName("modifier_option_id")
    val modifierOptionId: String?,
    @SerializedName("group_name")
    val groupName: String,
    @SerializedName("option_name")
    val optionName: String,
    @SerializedName("price_delta")
    val priceDelta: Long,
)

fun HeldOrderResponse.toDomain(): HeldOrder = HeldOrder(
    id = heldOrderId,
    outletId = outletId,
    cashierSessionId = cashierSessionId,
    cashierUserId = cashierUserId,
    cashierName = cashierName,
    actorRole = actorRole,
    label = label,
    status = status,
    version = version,
    subtotalEstimate = subtotalEstimate,
    taxEstimate = taxEstimate,
    totalEstimate = totalEstimate,
    itemCount = itemCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    cancelledAt = cancelledAt,
    convertedAt = convertedAt,
    convertedTransactionId = convertedTransactionId,
    items = items.map { it.toDomain() },
)

fun HeldOrderItemResponse.toDomain(): HeldOrderItem = HeldOrderItem(
    id = heldOrderItemId,
    productId = productId,
    quantity = quantity,
    productName = productName,
    sku = sku,
    basePrice = basePrice,
    effectivePrice = effectivePrice,
    lineSubtotal = lineSubtotal,
    lineDiscount = lineDiscount,
    lineTotal = lineTotal,
    note = note,
    displayOrder = displayOrder,
    modifiers = modifiers.map { it.toDomain() },
)

fun HeldOrderItemModifierResponse.toDomain(): HeldOrderModifier = HeldOrderModifier(
    id = heldOrderItemModifierId,
    modifierGroupId = modifierGroupId,
    modifierOptionId = modifierOptionId,
    groupName = groupName,
    optionName = optionName,
    priceDelta = priceDelta,
)
