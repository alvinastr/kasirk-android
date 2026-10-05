package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CartUpdateResult
import javax.inject.Inject

class UpdateCartQuantityUseCase @Inject constructor(
    private val cartRepository: CartRepository,
) {
    operator fun invoke(
        lineKey: String,
        quantity: Int,
    ): CartUpdateResult = cartRepository.updateQuantity(lineKey, quantity)
}
