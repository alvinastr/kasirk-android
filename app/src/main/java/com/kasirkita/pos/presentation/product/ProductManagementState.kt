package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Product

sealed interface ProductManagementState {
    data object Idle : ProductManagementState
    data object Loading : ProductManagementState
    data class Success(val product: Product) : ProductManagementState
    data class Error(val message: String) : ProductManagementState
}
