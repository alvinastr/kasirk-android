package com.kasirkita.pos.presentation.authv2

sealed interface LogoutState {
    data object Idle : LogoutState
    data object Loading : LogoutState
    data object LoggedOut : LogoutState
    data class Blocked(val unresolvedCount: Int) : LogoutState
    data class Error(val message: String) : LogoutState
}
