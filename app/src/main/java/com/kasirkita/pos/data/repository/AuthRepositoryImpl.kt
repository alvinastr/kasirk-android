package com.kasirkita.pos.data.repository

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.core.datastore.TokenDataStore
import com.kasirkita.pos.data.api.AuthApi
import com.kasirkita.pos.data.model.LoginRequest
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.model.UserSession
import com.kasirkita.pos.domain.repository.AuthRepository
import retrofit2.HttpException
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore,
    private val gson: Gson,
) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String,
        tenantId: String,
    ): Result<UserSession> = runCatching {
        val response = authApi.login(
            LoginRequest(
                email = email,
                password = password,
                tenantId = tenantId,
            ),
        )

        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        val accessToken = response.body()?.accessToken
            ?.takeIf(String::isNotBlank)
            ?: error("Login response does not contain an access token")

        val session = decodeSession(accessToken)
        tokenDataStore.saveSession(session)
        session
    }

    private fun decodeSession(accessToken: String): UserSession {
        val tokenParts = accessToken.split('.')
        require(tokenParts.size >= JWT_MINIMUM_PARTS) { "Invalid JWT format" }

        val payloadJson = String(
            Base64.decode(
                tokenParts[JWT_PAYLOAD_INDEX],
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            ),
            StandardCharsets.UTF_8,
        )
        val payload = gson.fromJson(payloadJson, JwtPayload::class.java)

        val userId = payload.userId?.takeIf(String::isNotBlank)
            ?: error("JWT payload does not contain sub")
        val tenantId = payload.tenantId?.takeIf(String::isNotBlank)
            ?: error("JWT payload does not contain tenant_id")
        val roleValue = payload.role?.takeIf(String::isNotBlank)
            ?: error("JWT payload does not contain role")
        val role = runCatching {
            UserRole.valueOf(roleValue.uppercase(Locale.ROOT))
        }.getOrElse {
            error("JWT payload contains an unsupported role")
        }

        return UserSession(
            accessToken = accessToken,
            tenantId = tenantId,
            userId = userId,
            role = role,
        )
    }

    private data class JwtPayload(
        @SerializedName("sub")
        val userId: String?,
        @SerializedName("tenant_id")
        val tenantId: String?,
        val role: String?,
    )

    private companion object {
        const val JWT_MINIMUM_PARTS = 2
        const val JWT_PAYLOAD_INDEX = 1
    }
}
