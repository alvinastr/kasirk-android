package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartRepository
import javax.inject.Inject

class AddToCartUseCase @Inject constructor(
    private val cartRepository: CartRepository,
) {
    operator fun invoke(product: Product) {
        cartRepository.addProduct(product)
    }
}
