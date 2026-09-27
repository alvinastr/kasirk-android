package com.kasirkita.pos.core.network

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.TokenDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeAuthTokenProvider @Inject constructor(
    private val authSessionDataStore: AuthSessionDataStore,
    private val tokenDataStore: TokenDataStore,
) : AuthTokenProvider {
    override suspend fun getToken(): String? =
        authSessionDataStore.getAccessToken() ?: tokenDataStore.getToken()
}
