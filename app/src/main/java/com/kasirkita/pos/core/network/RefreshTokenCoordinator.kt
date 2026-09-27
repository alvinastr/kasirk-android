package com.kasirkita.pos.core.network

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.data.api.AuthV2Api
import com.kasirkita.pos.data.model.RefreshTokenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshTokenCoordinator internal constructor(
    private val authV2Api: AuthV2Api,
    private val authSessionDataStore: AuthSessionDataStore,
    private val currentTimeMillis: () -> Long,
) {
    @Inject
    constructor(
        @RefreshClient authV2Api: AuthV2Api,
        authSessionDataStore: AuthSessionDataStore,
    ) : this(
        authV2Api = authV2Api,
        authSessionDataStore = authSessionDataStore,
        currentTimeMillis = System::currentTimeMillis,
    )

    private val refreshMutex = Mutex()

    suspend fun refreshAccessToken(failedAccessToken: String?): String? =
        refreshMutex.withLock {
            val session = authSessionDataStore.getSession() ?: return@withLock null

            if (
                failedAccessToken != null &&
                session.accessToken != failedAccessToken
            ) {
                return@withLock session.accessToken
            }

            try {
                val response = authV2Api.refreshToken(
                    RefreshTokenRequest(session.refreshToken),
                )
                require(response.accessToken.isNotBlank()) {
                    "Refresh response does not contain an access token"
                }
                require(response.refreshToken.isNotBlank()) {
                    "Refresh response does not contain a refresh token"
                }
                require(response.expiresIn > 0L) {
                    "Refresh response contains an invalid expiry"
                }

                val expiresAt = Math.addExact(
                    currentTimeMillis(),
                    Math.multiplyExact(response.expiresIn, MILLIS_PER_SECOND),
                )
                authSessionDataStore.updateTokenPair(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresAt = expiresAt,
                )
                response.accessToken
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                authSessionDataStore.clearSession()
                null
            }
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
