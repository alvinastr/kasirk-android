package com.kasirkita.pos.presentation.modifier

import com.kasirkita.pos.domain.model.ModifierGroup

sealed interface ModifierGroupListState {
    data object Loading : ModifierGroupListState
    data class Success(val groups: List<ModifierGroup>) : ModifierGroupListState
    data class Error(val message: String) : ModifierGroupListState
}

sealed interface ModifierGroupActionState {
    data object Idle : ModifierGroupActionState
    data object Loading : ModifierGroupActionState
    data class Success(val message: String) : ModifierGroupActionState
    data class Error(val message: String) : ModifierGroupActionState
}