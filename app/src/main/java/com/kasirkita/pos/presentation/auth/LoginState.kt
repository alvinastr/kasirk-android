package com.kasirkita.pos.presentation.auth

import com.kasirkita.pos.domain.model.UserSession

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Success(val session: UserSession) : LoginState
    data class Error(val message: String) : LoginState
}
