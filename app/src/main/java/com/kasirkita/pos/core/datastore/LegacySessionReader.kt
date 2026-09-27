package com.kasirkita.pos.core.datastore

import com.kasirkita.pos.domain.model.UserSession
import javax.inject.Inject
import javax.inject.Singleton

interface LegacySessionReader {
    suspend fun getSession(): UserSession?
}

@Singleton
class TokenDataStoreLegacySessionReader @Inject constructor(
    private val tokenDataStore: TokenDataStore,
) : LegacySessionReader {
    override suspend fun getSession(): UserSession? = tokenDataStore.getSession()
}
