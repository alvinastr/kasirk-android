package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.repository.CartRepository
import javax.inject.Inject

class RemoveFromCartUseCase @Inject constructor(
    private val cartRepository: CartRepository,
) {
    operator fun invoke(productId: String) {
        cartRepository.removeProduct(productId)
    }
}
