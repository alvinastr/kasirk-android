package com.kasirkita.pos.domain.model

data class CartItem(
    val lineKey: CartLineKey,
    val productId: String,
    val name: String,
    val sku: String,
    val basePrice: Long,
    val quantity: Int,
    val modifierSelections: List<CartModifierSelectionSnapshot> = emptyList(),
    val note: String? = null,
    val trackStock: Boolean = false,
    val availableStock: Int? = null,
) {
    init {
        require(productId == lineKey.productId) { "Cart line productId must match its key" }
        require(note == lineKey.note) { "Cart line note must be normalized in its key" }
        require(modifierSelections.map { it.optionId }.distinct().sorted() == lineKey.modifierOptionIds) {
            "Cart line modifiers must match its key"
        }
    }

    val price: Long
        get() = basePrice + modifierSelections.sumOf { it.priceDelta }

    val modifierOptionIds: List<String>
        get() = lineKey.modifierOptionIds

    fun subtotal(): Long = price * quantity.toLong()

    fun canIncreaseQuantity(): Boolean =
        !trackStock || availableStock == null || quantity < availableStock
}
