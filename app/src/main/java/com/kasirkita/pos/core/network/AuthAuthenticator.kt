package com.kasirkita.pos.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthAuthenticator @Inject constructor(
    private val refreshTokenCoordinator: RefreshTokenCoordinator,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.normalizedPath() in EXCLUDED_PATHS) {
            return null
        }
        if (response.responseCount() >= MAX_RESPONSE_COUNT) {
            return null
        }

        val failedAccessToken = response.request.bearerToken()
        val refreshedAccessToken = runBlocking {
            refreshTokenCoordinator.refreshAccessToken(failedAccessToken)
        } ?: return null

        if (failedAccessToken == refreshedAccessToken) {
            return null
        }

        return response.request.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX $refreshedAccessToken")
            .build()
    }

    private fun String.normalizedPath(): String = trimEnd('/').ifEmpty { "/" }

    private fun Request.bearerToken(): String? {
        val authorization = header(AUTHORIZATION_HEADER) ?: return null
        if (!authorization.startsWith(BEARER_PREFIX_WITH_SPACE, ignoreCase = true)) {
            return null
        }
        return authorization.substring(BEARER_PREFIX_WITH_SPACE.length)
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun Response.responseCount(): Int {
        var count = 1
        var previous = priorResponse
        while (previous != null) {
            count += 1
            previous = previous.priorResponse
        }
        return count
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer"
        const val BEARER_PREFIX_WITH_SPACE = "$BEARER_PREFIX "
        const val MAX_RESPONSE_COUNT = 2

        val EXCLUDED_PATHS = setOf(
            "/auth/v2/refresh",
            "/auth/v2/pin/login",
            "/auth/v2/logout",
        )
    }
}
