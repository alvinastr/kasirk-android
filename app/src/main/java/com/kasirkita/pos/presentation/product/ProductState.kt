package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Product

sealed interface ProductState {
    data object Loading : ProductState
    data class Success(val products: List<Product>) : ProductState
    data class Error(val message: String) : ProductState
}
