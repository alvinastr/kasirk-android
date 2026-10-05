package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.CartModifierSelectionSnapshot
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import javax.inject.Inject

class AddToCartUseCase @Inject constructor(
    private val cartRepository: CartRepository,
) {
    operator fun invoke(
        product: Product,
        availableStock: Int? = null,
    ): CartUpdateResult = cartRepository.addProduct(product, availableStock)

    operator fun invoke(
        product: Product,
        selectedModifiers: List<CartModifierSelectionSnapshot>,
        note: String?,
        availableStock: Int? = null,
    ): CartUpdateResult = cartRepository.addConfiguredProduct(product, selectedModifiers, note, availableStock)
}
