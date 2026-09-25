package com.kasirkita.pos.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.usecase.GetTransactionDetailUseCase
import com.kasirkita.pos.domain.usecase.GetTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val getTransactions: GetTransactionsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<TransactionHistoryState>(
        TransactionHistoryState.Loading,
    )
    val state: StateFlow<TransactionHistoryState> = _state.asStateFlow()

    init {
        loadTransactions()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _state.value = TransactionHistoryState.Loading
            _state.value = transactionHistoryState(getTransactions())
        }
    }
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTransactionDetail: GetTransactionDetailUseCase,
) : ViewModel() {

    private val transactionId = requireNotNull(
        savedStateHandle.get<String>(TRANSACTION_ID_ARGUMENT),
    )
    private val _state = MutableStateFlow<TransactionDetailState>(
        TransactionDetailState.Loading,
    )
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

    init {
        loadTransaction()
    }

    fun loadTransaction() {
        viewModelScope.launch {
            _state.value = TransactionDetailState.Loading
            _state.value = transactionDetailState(getTransactionDetail(transactionId))
        }
    }
}

internal fun transactionHistoryState(
    result: Result<List<Transaction>>,
): TransactionHistoryState = result.fold(
    onSuccess = { transactions ->
        if (transactions.isEmpty()) {
            TransactionHistoryState.Empty
        } else {
            TransactionHistoryState.Success(transactions)
        }
    },
    onFailure = { throwable ->
        TransactionHistoryState.Error(transactionHistoryErrorMessage(throwable))
    },
)

internal fun transactionDetailState(
    result: Result<Transaction>,
): TransactionDetailState = result.fold(
    onSuccess = TransactionDetailState::Success,
    onFailure = { throwable ->
        TransactionDetailState.Error(transactionHistoryErrorMessage(throwable))
    },
)

internal fun transactionHistoryErrorMessage(throwable: Throwable): String = when {
    throwable is IOException ->
        "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    throwable is HttpException && throwable.code() == 401 ->
        "Sesi sudah berakhir. Silakan login kembali."
    throwable is HttpException && throwable.code() == 403 ->
        "Anda tidak memiliki akses ke transaksi ini."
    throwable is HttpException && throwable.code() == 404 ->
        "Transaksi tidak ditemukan."
    else -> "Riwayat transaksi tidak dapat dimuat. Coba lagi."
}

internal const val TRANSACTION_ID_ARGUMENT = "transactionId"
