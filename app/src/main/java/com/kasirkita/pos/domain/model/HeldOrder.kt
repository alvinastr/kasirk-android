package com.kasirkita.pos.domain.model

data class HeldOrder(
    val id: String,
    val outletId: String,
    val cashierSessionId: String,
    val cashierUserId: String,
    val cashierName: String?,
    val actorRole: String?,
    val label: String?,
    val status: String,
    val version: Int,
    val subtotalEstimate: Long,
    val taxEstimate: Long,
    val totalEstimate: Long,
    val itemCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val cancelledAt: String?,
    val convertedAt: String?,
    val convertedTransactionId: String? = null,
    val items: List<HeldOrderItem> = emptyList(),
)

data class HeldOrderItem(
    val id: String,
    val productId: String,
    val quantity: Int,
    val productName: String,
    val sku: String,
    val basePrice: Long,
    val effectivePrice: Long,
    val lineSubtotal: Long,
    val lineDiscount: Long,
    val lineTotal: Long,
    val note: String?,
    val displayOrder: Int,
    val modifiers: List<HeldOrderModifier> = emptyList(),
)

data class HeldOrderModifier(
    val id: String,
    val modifierGroupId: String?,
    val modifierOptionId: String?,
    val groupName: String,
    val optionName: String,
    val priceDelta: Long,
)

enum class HeldOrderStatus { OPEN, CONVERTED, CANCELLED }

data class HeldOrderItemRequest(
    val productId: String,
    val quantity: Int,
    val modifierOptionIds: List<String> = emptyList(),
    val note: String? = null,
)

data class HeldOrderCreateRequest(
    val outletId: String,
    val cashierSessionId: String,
    val label: String? = null,
    val items: List<HeldOrderItemRequest>,
)

data class HeldOrderUpdateRequest(
    val id: String,
    val expectedVersion: Int,
    val label: String? = null,
    val items: List<HeldOrderItemRequest>? = null,
)

data class HeldOrderCheckoutRequest(
    val id: String,
    val expectedVersion: Int,
    val clientTransactionId: String,
    val payment: V1Payment,
)

data class HeldOrderCheckoutResult(
    val transaction: Transaction,
    val replayed: Boolean,
)
