package com.kasirkita.pos.core.network

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthV2TokenProvider @Inject constructor(
    private val authSessionDataStore: AuthSessionDataStore,
) : AuthTokenProvider {
    override suspend fun getToken(): String? = authSessionDataStore.getAccessToken()
}
