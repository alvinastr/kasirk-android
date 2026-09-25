package com.kasirkita.pos.presentation.shift

import com.kasirkita.pos.domain.model.Shift

sealed interface ShiftState {
    data object Loading : ShiftState
    data object NoShift : ShiftState
    data object Opening : ShiftState
    data class ShiftLoaded(val shift: Shift) : ShiftState
    data class Closing(val shift: Shift) : ShiftState
    data class ShiftClosed(val shift: Shift) : ShiftState
    data class Error(val message: String) : ShiftState
}
