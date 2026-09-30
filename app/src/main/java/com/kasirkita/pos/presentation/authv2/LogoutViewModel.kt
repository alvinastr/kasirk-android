package com.kasirkita.pos.presentation.authv2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.session.SessionBoundaryCleaner
import com.kasirkita.pos.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LogoutViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val authSessionDataStore: AuthSessionDataStore,
    private val sessionBoundaryCleaner: SessionBoundaryCleaner,
) : ViewModel() {

    private val _state = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val state: StateFlow<LogoutState> = _state.asStateFlow()

    fun logout() {
        if (_state.value == LogoutState.Loading) return

        _state.value = LogoutState.Loading
        viewModelScope.launch {
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
