package com.kasirkita.pos.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.GetTransactionDetailUseCase
import com.kasirkita.pos.domain.usecase.GetTransactionsUseCase
import com.kasirkita.pos.domain.usecase.PrintReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val getTransactions: GetTransactionsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<TransactionHistoryState>(
        TransactionHistoryState.Loading,
    )
    val state: StateFlow<TransactionHistoryState> = _state.asStateFlow()

    // Date filter state - defaults to Today (local calendar day, sent as UTC half-open [from, to))
    private val _fromDate = MutableStateFlow<String?>(todayStartUtc())
    private val _toDate = MutableStateFlow<String?>(todayEndExclusiveUtc())
    val fromDate: StateFlow<String?> = _fromDate.asStateFlow()
    val toDate: StateFlow<String?> = _toDate.asStateFlow()

    init {
        loadTransactions()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _state.value = TransactionHistoryState.Loading
            _state.value = transactionHistoryState(
                getTransactions(_fromDate.value, _toDate.value)
            )
        }
    }

    fun setDateFilter(from: String?, to: String?) {
        _fromDate.value = from
        _toDate.value = to
        loadTransactions()
    }

    fun setLocalDateFilter(from: String?, to: String?) {
        val zone = ZoneId.systemDefault()
        _fromDate.value = from?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toString() }
        _toDate.value = to?.let { LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toInstant().toString() }
        loadTransactions()
    }

    fun clearDateFilter() {
        _fromDate.value = null
        _toDate.value = null
        loadTransactions()
    }

    /** Returns the start of today in the device timezone as an ISO-8601 UTC instant. */
    private fun todayStartUtc(): String =
        localDateToUtcRange(LocalDate.now(), ZoneId.systemDefault()).first

    /** Returns the start of tomorrow in the device timezone as an ISO-8601 UTC instant (exclusive upper bound). */
    private fun todayEndExclusiveUtc(): String =
        localDateToUtcRange(LocalDate.now(), ZoneId.systemDefault()).second
}

/** Converts a local calendar date to canonical half-open UTC instant range [from, to). */
internal fun localDateToUtcRange(localDate: LocalDate, zoneId: ZoneId): Pair<String, String> {
    val from = localDate.atStartOfDay(zoneId).toInstant().toString()
    val toExclusive = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toString()
    return from to toExclusive
}

@HiltViewModel
class TransactionDetailViewModel @Inject internal constructor(
    savedStateHandle: SavedStateHandle,
    private val getTransactionDetail: GetTransactionDetailUseCase,
    private val getReceipt: GetReceiptUseCase,
    private val printReceipt: PrintReceiptUseCase,
) : ViewModel() {

    private val transactionId = requireNotNull(
        savedStateHandle.get<String>(TRANSACTION_ID_ARGUMENT),
    )
    private val _state = MutableStateFlow<TransactionDetailState>(
        TransactionDetailState.Loading,
    )
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()
    private val _printState = MutableStateFlow<TransactionPrintState>(TransactionPrintState.Idle)
    val printState: StateFlow<TransactionPrintState> = _printState.asStateFlow()

    init {
        loadTransaction()
    }

    fun loadTransaction() {
        viewModelScope.launch {
            _state.value = TransactionDetailState.Loading
            _state.value = transactionDetailState(getTransactionDetail(transactionId))
        }
    }

    fun printReceipt() {
        if (_printState.value is TransactionPrintState.Loading) return
        val transaction = (_state.value as? TransactionDetailState.Success)?.transaction ?: return
        if (transaction.status != "completed") return

        _printState.value = TransactionPrintState.Loading
        viewModelScope.launch {
            val receiptResult = getReceipt(transaction.id)
            val receipt = receiptResult.getOrNull()
            if (receipt == null) {
                _printState.value = TransactionPrintState.Error("Struk tidak dapat dimuat. Coba lagi.")
                return@launch
            }

            printReceipt(receipt).fold(
                onSuccess = { _printState.value = TransactionPrintState.Success },
                onFailure = { error ->
                    _printState.value = TransactionPrintState.Error(
                        error.message ?: "Struk gagal dicetak.",
                    )
                },
            )
        }
    }

    fun dismissPrintError() {
        if (_printState.value is TransactionPrintState.Error) {
            _printState.value = TransactionPrintState.Idle
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
