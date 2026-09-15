package com.kasirkita.pos.presentation.cart

import com.kasirkita.pos.domain.model.Cart

data class CartState(
    val cart: Cart = Cart(),
)
