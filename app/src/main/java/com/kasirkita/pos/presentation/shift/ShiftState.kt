package com.kasirkita.pos.presentation.shift

import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.ShiftSummary

sealed interface ShiftState {
    data object Loading : ShiftState
    data object NoShift : ShiftState
    data object Opening : ShiftState
    data class ShiftLoaded(
        val shift: Shift,
        val summaryError: String? = null,
    ) : ShiftState
    data class LoadingSummary(val shift: Shift) : ShiftState
    data class SummaryLoaded(
        val shift: Shift,
        val summary: ShiftSummary,
        val closeError: String? = null,
    ) : ShiftState
    data class Closing(
        val shift: Shift,
        val summary: ShiftSummary,
    ) : ShiftState
    data class ShiftClosed(
        val shift: Shift,
        val summary: ShiftSummary,
    ) : ShiftState
    data class Error(val message: String) : ShiftState
}