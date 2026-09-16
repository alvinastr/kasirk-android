package com.kasirkita.pos.presentation.shift

import com.kasirkita.pos.domain.model.Shift

sealed interface ShiftState {
    data object Loading : ShiftState
    data object NoShift : ShiftState
    data class ShiftLoaded(val shift: Shift) : ShiftState
    data class Error(val message: String) : ShiftState
}
