package com.kasirkita.pos.presentation.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getReceipt: GetReceiptUseCase,
) : ViewModel() {

    private val transactionId: String? = savedStateHandle[TRANSACTION_ID_ARGUMENT]
    private val _state = MutableStateFlow<ReceiptState>(ReceiptState.Loading)
    val state: StateFlow<ReceiptState> = _state.asStateFlow()

    init {
        loadReceipt()
    }

    fun loadReceipt() {
        val id = transactionId
        if (id.isNullOrBlank()) {
            _state.value = ReceiptState.Error("Transaction ID tidak tersedia")
            return
        }

        viewModelScope.launch {
            _state.value = ReceiptState.Loading
            getReceipt(id).fold(
                onSuccess = { receipt ->
                    _state.value = ReceiptState.Success(receipt)
                },
                onFailure = { throwable ->
                    _state.value = ReceiptState.Error(
                        throwable.message ?: "Gagal mengambil receipt",
                    )
                },
            )
        }
    }

    private companion object {
        const val TRANSACTION_ID_ARGUMENT = "transactionId"
    }
}
