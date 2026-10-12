package com.kasirkita.pos.presentation.authv2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.session.SessionBoundaryCleaner
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import com.kasirkita.pos.domain.repository.OnlineTransactionJournalRepository
import com.kasirkita.pos.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LogoutViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val authSessionDataStore: AuthSessionDataStore,
    private val sessionBoundaryCleaner: SessionBoundaryCleaner,
    private val offlineSyncRepository: OfflineSyncRepository,
    private val onlineTransactionJournalRepository: OnlineTransactionJournalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val state: StateFlow<LogoutState> = _state.asStateFlow()

    fun logout() {
        if (_state.value == LogoutState.Loading) return

        _state.value = LogoutState.Loading
        viewModelScope.launch {
            val unresolvedCount = try {
                val session = authSessionDataStore.getSession()
                val legacyCount = offlineSyncRepository.observeQueueSummary().first().let { summary ->
                    summary.pendingCount + summary.failedCount
                }
                val journalCount = session?.let {
                    onlineTransactionJournalRepository.observeUnresolvedCount(
                        tenantId = it.tenantId,
                        userId = it.userId,
                    ).first()
                } ?: 0
                legacyCount + journalCount
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.value = LogoutState.Error(
                    "Transaksi offline belum dapat diperiksa. Selesaikan sinkronisasi lalu coba lagi.",
                )
                return@launch
            }

            if (unresolvedCount > 0) {
                _state.value = LogoutState.Blocked(unresolvedCount)
                return@launch
            }

            val refreshToken = readRefreshToken()

            try {
                if (refreshToken != null) {
                    logoutUseCase(refreshToken)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Unit
            } finally {
                val cleanupFailure = withContext(NonCancellable) {
                    clearLocalState()
                }
                _state.value = if (cleanupFailure == null) {
                    LogoutState.LoggedOut
                } else {
                    LogoutState.Error(
                        "Logout lokal belum selesai. Coba lagi.",
                    )
                }
            }
        }
    }

    fun acknowledgeLoggedOut() {
        if (_state.value == LogoutState.LoggedOut) {
            _state.value = LogoutState.Idle
        }
    }

    private suspend fun readRefreshToken(): String? = try {
        authSessionDataStore.getSession()?.refreshToken
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private suspend fun clearLocalState(): Throwable? {
        val currentIdentity = try {
            authSessionDataStore.getSession()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        var firstFailure = sessionBoundaryCleaner.clear(
            tenantId = currentIdentity?.tenantId,
            userId = currentIdentity?.userId,
        )

        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }

        attempt { authSessionDataStore.clearSession() }

        return firstFailure
    }
}
