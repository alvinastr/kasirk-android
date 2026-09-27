package com.kasirkita.pos.core.datastore

import com.kasirkita.pos.domain.model.UserSession
import javax.inject.Inject
import javax.inject.Singleton

interface LegacySessionReader {
    suspend fun getSession(): UserSession?
}

interface LegacySessionCleaner {
    suspend fun clearSession()
}

@Singleton
class TokenDataStoreLegacySessionReader @Inject constructor(
    private val tokenDataStore: TokenDataStore,
) : LegacySessionReader, LegacySessionCleaner {
    override suspend fun getSession(): UserSession? = tokenDataStore.getSession()

    override suspend fun clearSession() = tokenDataStore.clearSession()
}
