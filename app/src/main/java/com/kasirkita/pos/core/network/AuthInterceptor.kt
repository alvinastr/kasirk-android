package com.kasirkita.pos.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the latest in-memory access token to synchronous OkHttp requests.
 * A DataStore-backed component can update the provider implementation later.
 */
fun interface AuthTokenProvider {
    fun getToken(): String?
}

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenProvider: AuthTokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.getToken()?.trim()

        if (token.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val authenticatedRequest = request.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer"
    }
}
