package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.repository.CartRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class GetCartUseCase @Inject constructor(
    private val cartRepository: CartRepository,
) {
    operator fun invoke(): StateFlow<Cart> = cartRepository.getCart()
}
