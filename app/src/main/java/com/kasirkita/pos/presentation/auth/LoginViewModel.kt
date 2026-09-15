package com.kasirkita.pos.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun login(
        email: String,
        password: String,
        tenantId: String,
    ) {
        if (email.isBlank() || password.isBlank() || tenantId.isBlank()) {
            _state.value = LoginState.Error("Email, password, dan Tenant ID wajib diisi")
            return
        }

        if (_state.value is LoginState.Loading) return

        viewModelScope.launch {
            _state.value = LoginState.Loading
            loginUseCase(
                email = email.trim(),
                password = password,
                tenantId = tenantId.trim(),
            ).fold(
                onSuccess = { session ->
                    _state.value = LoginState.Success(session)
                },
                onFailure = { throwable ->
                    _state.value = LoginState.Error(
                        throwable.message ?: "Login gagal. Silakan coba lagi.",
                    )
                },
            )
        }
    }
}
