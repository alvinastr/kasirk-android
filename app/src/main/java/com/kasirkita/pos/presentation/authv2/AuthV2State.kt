package com.kasirkita.pos.presentation.authv2

import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.ResolvedStore
import com.kasirkita.pos.domain.model.StoreUser

sealed interface AuthV2State {
    data class StoreLogin(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : AuthV2State

    data class UserSelection(
        val store: ResolvedStore,
    ) : AuthV2State

    data class PinLogin(
        val store: ResolvedStore,
        val user: StoreUser,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : AuthV2State

    data class Authenticated(
        val session: AuthSession,
    ) : AuthV2State
}
