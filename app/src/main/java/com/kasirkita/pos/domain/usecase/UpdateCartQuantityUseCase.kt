package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.repository.CartRepository
import javax.inject.Inject

class UpdateCartQuantityUseCase @Inject constructor(
    private val cartRepository: CartRepository,
) {
    operator fun invoke(productId: String, quantity: Int) {
        cartRepository.updateQuantity(productId, quantity)
    }
}
