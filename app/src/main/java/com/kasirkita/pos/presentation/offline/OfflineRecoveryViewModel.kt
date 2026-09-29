package com.kasirkita.pos.presentation.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.OfflineTransactionFailureType
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OfflineRecoveryViewModel @Inject constructor(
    private val repository: OfflineSyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OfflineRecoveryState())
    val state: StateFlow<OfflineRecoveryState> = _state.asStateFlow()

    init {
        loadTransactions()
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    noticeMessage = null,
                    errorMessage = null,
                )
            }
            refreshTransactions()
        }
    }

    fun retry(clientTransactionId: String) {
        performAction(
            clientTransactionId = clientTransactionId,
            requiredType = OfflineTransactionFailureType.REJECTED,
        ) {
            repository.retryFailedTransaction(clientTransactionId).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is SyncOutcome.Completed -> "Transaksi berhasil disinkronkan."
                            .asNotice()
                        is SyncOutcome.RetryableFailure -> ActionFeedback(
                            errorMessage = offlineRecoveryErrorMessage(outcome.message),
                        )
                        SyncOutcome.AuthenticationUnavailable -> ActionFeedback(
                            errorMessage = "Sesi login tidak tersedia. Silakan masuk kembali.",
                        )
                        is SyncOutcome.ActionRequired -> ActionFeedback(
                            errorMessage = "Transaksi masih memerlukan pemeriksaan.",
                        )
                    }
                },
                onFailure = { throwable -> throw throwable },
            )
        }
    }

    fun delete(clientTransactionId: String) {
        performAction(
            clientTransactionId = clientTransactionId,
            requiredType = OfflineTransactionFailureType.REJECTED,
        ) {
            repository.deleteFailedTransaction(clientTransactionId).getOrThrow()
            "Transaksi offline dihapus dari perangkat.".asNotice()
        }
    }

    fun acknowledge(clientTransactionId: String) {
        performAction(
            clientTransactionId = clientTransactionId,
            requiredType = OfflineTransactionFailureType.RECONCILIATION_REQUIRED,
        ) {
            repository.acknowledgeReconciliation(clientTransactionId).getOrThrow()
            "Pemeriksaan transaksi telah dikonfirmasi.".asNotice()
        }
    }

    private fun performAction(
        clientTransactionId: String,
        requiredType: OfflineTransactionFailureType,
        operation: suspend () -> ActionFeedback,
    ) {
        if (_state.value.processingClientTransactionId != null) return
        val transaction = _state.value.transactions.firstOrNull {
            it.clientTransactionId == clientTransactionId
        }
        if (transaction?.failureType != requiredType) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    processingClientTransactionId = clientTransactionId,
                    noticeMessage = null,
                    errorMessage = null,
                )
            }
            runCatching { operation() }.fold(
                onSuccess = { feedback ->
                    refreshTransactions(
                        noticeMessage = feedback.noticeMessage,
                        errorMessage = feedback.errorMessage,
                    )
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            processingClientTransactionId = null,
                            errorMessage = offlineRecoveryErrorMessage(throwable),
                        )
                    }
                },
            )
        }
    }

    private suspend fun refreshTransactions(
        noticeMessage: String? = null,
        errorMessage: String? = null,
    ) {
        repository.getRecoveryTransactions().fold(
            onSuccess = { transactions ->
                _state.value = OfflineRecoveryState(
                    isLoading = false,
                    transactions = transactions,
                    noticeMessage = noticeMessage,
                    errorMessage = errorMessage,
                )
            },
            onFailure = { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        transactions = emptyList(),
                        processingClientTransactionId = null,
                        errorMessage = offlineRecoveryErrorMessage(throwable),
                    )
                }
            },
        )
    }

    private fun String.asNotice() = ActionFeedback(noticeMessage = this)

    private data class ActionFeedback(
        val noticeMessage: String? = null,
        val errorMessage: String? = null,
    )
}
