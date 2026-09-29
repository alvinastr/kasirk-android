package com.kasirkita.pos.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import com.kasirkita.pos.domain.usecase.SyncPendingTransactionsUseCase
import com.kasirkita.pos.presentation.offline.offlineRecoveryErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    offlineSyncRepository: OfflineSyncRepository,
    private val syncPendingTransactions: SyncPendingTransactionsUseCase,
) : ViewModel() {

    private val _syncState = MutableStateFlow(HomeSyncState())
    val syncState: StateFlow<HomeSyncState> = _syncState.asStateFlow()

    init {
        viewModelScope.launch {
            offlineSyncRepository.observeQueueSummary().collect { summary ->
                _syncState.update { state ->
                    state.copy(
                        pendingCount = summary.pendingCount,
                        actionRequiredCount = summary.failedCount,
                    )
                }
            }
        }
    }

    fun syncNow() {
        if (_syncState.value.isSyncing) return

        viewModelScope.launch {
            _syncState.update {
                it.copy(
                    isSyncing = true,
                    lastResult = null,
                    errorMessage = null,
                )
            }
            syncPendingTransactions().fold(
                onSuccess = { outcome ->
                    _syncState.update {
                        when (outcome) {
                            is SyncOutcome.Completed -> it.copy(
                                isSyncing = false,
                                lastResult = outcome.result,
                                errorMessage = null,
                            )
                            is SyncOutcome.RetryableFailure -> it.copy(
                                isSyncing = false,
                                lastResult = outcome.result,
                                errorMessage = offlineRecoveryErrorMessage(outcome.message),
                            )
                            SyncOutcome.AuthenticationUnavailable -> it.copy(
                                isSyncing = false,
                                errorMessage = "Sesi autentikasi tidak tersedia",
                            )
                            is SyncOutcome.ActionRequired -> it.copy(
                                isSyncing = false,
                                lastResult = outcome.result,
                                errorMessage = "Ada transaksi yang memerlukan tindakan",
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    _syncState.update {
                        it.copy(
                            isSyncing = false,
                            errorMessage = offlineRecoveryErrorMessage(throwable),
                        )
                    }
                },
            )
        }
    }
}
