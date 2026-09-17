package com.kasirkita.pos.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import com.kasirkita.pos.domain.usecase.SyncPendingTransactionsUseCase
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
            offlineSyncRepository.observePendingCount().collect { pendingCount ->
                _syncState.update { state ->
                    state.copy(pendingCount = pendingCount)
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
                onSuccess = { result ->
                    _syncState.update {
                        it.copy(
                            isSyncing = false,
                            lastResult = result,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _syncState.update {
                        it.copy(
                            isSyncing = false,
                            errorMessage = throwable.message ?: "Sinkronisasi gagal",
                        )
                    }
                },
            )
        }
    }
}
