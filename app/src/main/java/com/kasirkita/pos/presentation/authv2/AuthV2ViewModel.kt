package com.kasirkita.pos.presentation.authv2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.DeviceIdProvider
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.StoreUser
import com.kasirkita.pos.domain.usecase.PinLoginUseCase
import com.kasirkita.pos.domain.usecase.ResolveStoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AuthV2ViewModel @Inject constructor(
    private val resolveStoreUseCase: ResolveStoreUseCase,
    private val pinLoginUseCase: PinLoginUseCase,
    private val authSessionDataStore: AuthSessionDataStore,
    private val deviceIdProvider: DeviceIdProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthV2State>(AuthV2State.StoreLogin())
    val state: StateFlow<AuthV2State> = _state.asStateFlow()

    fun resolveStore(storeCode: String) {
        val current = _state.value as? AuthV2State.StoreLogin ?: return
        if (current.isLoading) return

        val normalizedCode = storeCode.trim().uppercase(Locale.ROOT)
        if (normalizedCode.isBlank()) {
            _state.value = current.copy(errorMessage = "Kode toko wajib diisi.")
            return
        }

        _state.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                resolveStoreUseCase(normalizedCode).getOrThrow()
            }.fold(
                onSuccess = { store ->
                    _state.value = AuthV2State.UserSelection(store)
                },
                onFailure = { throwable ->
                    _state.value = AuthV2State.StoreLogin(
                        errorMessage = authV2ErrorMessage(
                            throwable = throwable,
                            action = AuthV2Action.RESOLVE_STORE,
                        ),
                    )
                },
            )
        }
    }

    fun selectUser(user: StoreUser) {
        val current = _state.value as? AuthV2State.UserSelection ?: return
        val selectedUser = current.store.users.firstOrNull { candidate ->
            candidate.id == user.id
        } ?: return

        _state.value = AuthV2State.PinLogin(
            store = current.store,
            user = selectedUser,
        )
    }

    fun loginWithPin(
        pin: String,
        deviceName: String?,
    ) {
        val current = _state.value as? AuthV2State.PinLogin ?: return
        if (current.isLoading) return

        val normalizedPin = pin.trim()
        if (!PIN_PATTERN.matches(normalizedPin)) {
            _state.value = current.copy(
                errorMessage = "PIN harus terdiri dari 6 digit.",
            )
            return
        }

        _state.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val deviceId = deviceIdProvider.getDeviceId()
                val tokens = pinLoginUseCase(
                    tenantId = current.store.tenant.id,
                    userId = current.user.id,
                    pin = normalizedPin,
                    deviceId = deviceId,
                    deviceName = deviceName?.trim()?.takeIf(String::isNotBlank),
                ).getOrThrow()

                require(tokens.accessToken.isNotBlank()) {
                    "PIN login response does not contain an access token"
                }
                require(tokens.refreshToken.isNotBlank()) {
                    "PIN login response does not contain a refresh token"
                }
                require(tokens.expiresInSeconds > 0L) {
                    "PIN login response contains an invalid expiry"
                }

                val session = AuthSession(
                    userId = current.user.id,
                    userName = current.user.name,
                    tenantId = current.store.tenant.id,
                    role = current.user.role,
                    outletId = current.user.outletId,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresAt = Math.addExact(
                        System.currentTimeMillis(),
                        Math.multiplyExact(tokens.expiresInSeconds, MILLIS_PER_SECOND),
                    ),
                    deviceId = deviceId,
                )
                authSessionDataStore.saveSession(session)
                session
            }.fold(
                onSuccess = { session ->
                    _state.value = AuthV2State.Authenticated(session)
                },
                onFailure = { throwable ->
                    _state.value = current.copy(
                        isLoading = false,
                        errorMessage = authV2ErrorMessage(
                            throwable = throwable,
                            action = AuthV2Action.PIN_LOGIN,
                        ),
                    )
                },
            )
        }
    }

    fun returnToStoreLogin() {
        _state.value = AuthV2State.StoreLogin()
    }

    fun returnToUserSelection() {
        val current = _state.value as? AuthV2State.PinLogin ?: return
        _state.value = AuthV2State.UserSelection(current.store)
    }

    private companion object {
        val PIN_PATTERN = Regex("^\\d{6}$")
        const val MILLIS_PER_SECOND = 1_000L
    }
}
