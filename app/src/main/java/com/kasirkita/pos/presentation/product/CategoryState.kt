package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Category

sealed interface CategoryState {
    data object Loading : CategoryState

    data object Empty : CategoryState

    data class Success(val categories: List<Category>) : CategoryState

    data class Error(val message: String) : CategoryState
}
