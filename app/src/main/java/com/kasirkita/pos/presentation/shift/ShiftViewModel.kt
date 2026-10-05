package com.kasirkita.pos.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.CloseShiftUseCase
import com.kasirkita.pos.domain.usecase.GetCurrentShiftUseCase
import com.kasirkita.pos.domain.usecase.OpenShiftUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val getCurrentShiftUseCase: GetCurrentShiftUseCase,
    private val openShiftUseCase: OpenShiftUseCase,
    private val closeShiftUseCase: CloseShiftUseCase,
    private val shiftRepository: ShiftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ShiftState>(ShiftState.Loading)
    val state: StateFlow<ShiftState> = _state.asStateFlow()

    private val _shiftOpened = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shiftOpened: Flow<Unit> = _shiftOpened

    init {
        loadCurrentShift()
    }

    fun loadCurrentShift() {
        viewModelScope.launch {
            val cachedShift = shiftRepository.currentShift.value
                ?.takeIf { it.status.equals("OPEN", ignoreCase = true) }
            if (cachedShift != null) {
                _state.value = ShiftState.ShiftLoaded(cachedShift)
            } else {
                _state.value = ShiftState.Loading
            }

            val result = getCurrentShiftUseCase()
            if (result.isFailure && cachedShift != null) return@launch
            _state.value = shiftStateAfterLoad(result)
        }
    }

    fun openShift(outletId: String) {
        if (outletId.isBlank()) {
            _state.value = ShiftState.Error("Outlet belum dipilih.")
            return
        }

        viewModelScope.launch {
            _state.value = ShiftState.Opening
            shiftStateAfterOpen(openShiftUseCase(outletId)).let { _state.value = it }
            if (_state.value is ShiftState.ShiftLoaded) {
                _shiftOpened.tryEmit(Unit)
            }
        }
    }

    fun loadSummaryForClose() {
        val activeShift = activeShiftFromState() ?: run {
            _state.value = ShiftState.Error("Tidak ada shift aktif.")
            return
        }

        viewModelScope.launch {
            _state.value = ShiftState.LoadingSummary(activeShift)
            val result = shiftRepository.getShiftSummary(activeShift.id)
            _state.value = shiftStateAfterSummary(activeShift, result)
        }
    }

    fun confirmCloseShift() {
        val currentState = _state.value as? ShiftState.SummaryLoaded ?: return
        val activeShift = currentState.shift
        val summary = currentState.summary

        viewModelScope.launch {
            _state.value = ShiftState.Closing(activeShift, summary)
            val result = closeShiftUseCase(activeShift.id)
            _state.value = result.fold(
                onSuccess = { closedShift -> ShiftState.ShiftClosed(closedShift, summary) },
                onFailure = { throwable ->
                    ShiftState.SummaryLoaded(
                        shift = activeShift,
                        summary = summary,
                        closeError = shiftErrorMessage(throwable),
                    )
                },
            )
        }
    }

    fun startNewShift() {
        if (_state.value is ShiftState.ShiftClosed) {
            _state.value = ShiftState.NoShift
        }
    }

    private fun activeShiftFromState(): Shift? = when (val currentState = _state.value) {
        is ShiftState.ShiftLoaded -> currentState.shift
        is ShiftState.LoadingSummary -> currentState.shift
        is ShiftState.SummaryLoaded -> currentState.shift
        is ShiftState.Closing -> currentState.shift
        else -> null
    }
}

internal fun shiftStateAfterLoad(result: Result<Shift?>): ShiftState = result.fold(
    onSuccess = { shift ->
        if (shift == null) ShiftState.NoShift else ShiftState.ShiftLoaded(shift)
    },
    onFailure = { throwable -> ShiftState.Error(shiftErrorMessage(throwable)) },
)

internal fun shiftStateAfterOpen(result: Result<Shift>): ShiftState = result.fold(
    onSuccess = ShiftState::ShiftLoaded,
    onFailure = { throwable -> ShiftState.Error(shiftErrorMessage(throwable)) },
)

internal fun shiftStateAfterSummary(
    shift: Shift,
    result: Result<ShiftSummary>,
): ShiftState = result.fold(
    onSuccess = { summary -> ShiftState.SummaryLoaded(shift, summary) },
    onFailure = { throwable ->
        ShiftState.ShiftLoaded(
            shift = shift,
            summaryError = shiftErrorMessage(throwable),
        )
    },
)

internal fun shiftErrorMessage(throwable: Throwable): String = when {
    throwable is IOException ->
        "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    throwable is HttpException && throwable.code() == 400 ->
        "Permintaan shift tidak valid. Muat ulang lalu coba lagi."
    throwable is HttpException && throwable.code() == 401 ->
        "Sesi sudah berakhir. Silakan login kembali."
    throwable is HttpException && throwable.code() == 403 ->
        "Anda tidak memiliki akses untuk mengelola shift ini."
    throwable is HttpException && throwable.code() == 404 ->
        "Shift atau outlet tidak ditemukan."
    throwable is HttpException && throwable.code() == 409 ->
        "Status shift sudah berubah. Muat ulang lalu coba lagi."
    else -> "Operasi shift gagal. Coba lagi."
}