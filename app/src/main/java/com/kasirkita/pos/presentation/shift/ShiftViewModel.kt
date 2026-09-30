package com.kasirkita.pos.presentation.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.CloseShiftUseCase
import com.kasirkita.pos.domain.usecase.GetCurrentShiftUseCase
import com.kasirkita.pos.domain.usecase.OpenShiftUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _isPersisting = MutableStateFlow(false)
    val isPersisting: StateFlow<Boolean> = _isPersisting.asStateFlow()

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

    fun openShift(
        outletId: String,
        openingCash: Long,
    ) {
        if (outletId.isBlank()) {
            _state.value = ShiftState.Error("Outlet belum dipilih.")
            return
        }
        if (openingCash < 0L) {
            _state.value = ShiftState.Error("Kas awal tidak boleh negatif.")
            return
        }

        viewModelScope.launch {
            _state.value = ShiftState.Opening
            _isPersisting.value = true
            try {
                _state.value = shiftStateAfterOpen(
                    openShiftUseCase(
                        outletId = outletId,
                        openingCash = openingCash,
                    ),
                )
            } finally {
                _isPersisting.value = false
            }
        }
    }

    fun closeShift(closingCash: Long) {
        val activeShift = when (val currentState = _state.value) {
            is ShiftState.ShiftLoaded -> currentState.shift
            is ShiftState.Closing -> currentState.shift
            else -> null
        }
        if (activeShift == null) {
            _state.value = ShiftState.Error("Tidak ada shift aktif.")
            return
        }
        if (closingCash < 0L) {
            _state.value = ShiftState.Error("Kas akhir tidak boleh negatif.")
            return
        }

        viewModelScope.launch {
            _state.value = ShiftState.Closing(activeShift)
            _isPersisting.value = true
            try {
                _state.value = shiftStateAfterClose(
                    closeShiftUseCase(
                        shiftId = activeShift.id,
                        closingCash = closingCash,
                    ),
                )
            } finally {
                _isPersisting.value = false
            }
        }
    }

    fun startNewShift() {
        if (_state.value is ShiftState.ShiftClosed) {
            _state.value = ShiftState.NoShift
        }
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

internal fun shiftStateAfterClose(result: Result<Shift>): ShiftState = result.fold(
    onSuccess = ShiftState::ShiftClosed,
    onFailure = { throwable -> ShiftState.Error(shiftErrorMessage(throwable)) },
)

internal fun shiftErrorMessage(throwable: Throwable): String = when {
    throwable is IOException ->
        "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    throwable is HttpException && throwable.code() == 400 ->
        "Nilai kas tidak valid. Periksa kembali input Anda."
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
