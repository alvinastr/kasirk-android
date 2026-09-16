package com.kasirkita.pos.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ShiftState>(ShiftState.Loading)
    val state: StateFlow<ShiftState> = _state.asStateFlow()

    init {
        loadCurrentShift()
    }

    fun loadCurrentShift() {
        execute(
            request = shiftRepository::getCurrentShift,
            onSuccess = { shift ->
                _state.value = shift.toState()
            },
        )
    }

    fun openShift(
        outletId: String,
        openingCash: Long,
    ) {
        if (outletId.isBlank()) {
            _state.value = ShiftState.Error("Outlet ID wajib tersedia")
            return
        }
        if (openingCash < 0L) {
            _state.value = ShiftState.Error("Opening Cash tidak boleh negatif")
            return
        }

        execute(
            request = {
                shiftRepository.openShift(
                    outletId = outletId,
                    openingCash = openingCash,
                )
            },
            onSuccess = { shift ->
                _state.value = ShiftState.ShiftLoaded(shift)
            },
        )
    }

    fun closeShift(closingCash: Long) {
        val activeShift = (_state.value as? ShiftState.ShiftLoaded)?.shift
        if (activeShift == null) {
            _state.value = ShiftState.Error("Tidak ada shift aktif")
            return
        }
        if (closingCash < 0L) {
            _state.value = ShiftState.Error("Closing Cash tidak boleh negatif")
            return
        }

        execute(
            request = {
                shiftRepository.closeShift(
                    shiftId = activeShift.id,
                    closingCash = closingCash,
                )
            },
            onSuccess = {
                _state.value = ShiftState.NoShift
            },
        )
    }

    private fun <T> execute(
        request: suspend () -> Result<T>,
        onSuccess: (T) -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = ShiftState.Loading
            request().fold(
                onSuccess = onSuccess,
                onFailure = { throwable ->
                    _state.value = ShiftState.Error(
                        throwable.message ?: "Operasi shift gagal.",
                    )
                },
            )
        }
    }

    private fun Shift?.toState(): ShiftState =
        if (this == null) ShiftState.NoShift else ShiftState.ShiftLoaded(this)
}
