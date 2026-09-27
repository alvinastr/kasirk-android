package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.kasirkita.pos.data.model.AuthTokenResponse
import com.kasirkita.pos.data.model.CurrentUserResponse
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import com.kasirkita.pos.data.model.StoreResolveResponse
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthV2JsonTest {

    private val gson = Gson()

    @Test
    fun responses_parseSnakeCaseAndMapToDomain() {
        val store = gson.fromJson(STORE_RESPONSE_JSON, StoreResolveResponse::class.java).toDomain()
        val tokens = gson.fromJson(TOKEN_RESPONSE_JSON, AuthTokenResponse::class.java).toDomain()
        val currentUser = gson.fromJson(
            CURRENT_USER_RESPONSE_JSON,
            CurrentUserResponse::class.java,
        ).toDomain()

        assertEquals("tenant-id", store.tenant.id)
        assertEquals("Kasir Utama", store.users.single().name)
        assertEquals(UserRole.CASHIER, store.users.single().role)
        assertNull(store.users.single().outletId)
        assertEquals("access-token", tokens.accessToken)
        assertEquals("refresh-token", tokens.refreshToken)
        assertEquals(900L, tokens.expiresInSeconds)
        assertEquals("tenant-id", currentUser.tenantId)
        assertEquals("outlet-id", currentUser.outletId)
        assertEquals(UserRole.ADMIN, currentUser.role)
    }

    @Test
    fun requests_serializeUsingBackendFieldNames() {
        val resolveJson = json(StoreResolveRequest("TOKO-01"))
        val pinJson = json(
            PinLoginRequest(
                tenantId = "tenant-id",
                userId = "user-id",
                pin = "123456",
                deviceId = "device-id",
                deviceName = "Kasir Depan",
            ),
        )
        val refreshJson = json(RefreshTokenRequest("refresh-token"))
        val logoutJson = json(LogoutRequest("refresh-token"))

        assertEquals("TOKO-01", resolveJson["store_code"].asString)
        assertEquals("tenant-id", pinJson["tenant_id"].asString)
        assertEquals("user-id", pinJson["user_id"].asString)
        assertEquals("device-id", pinJson["device_id"].asString)
        assertEquals("Kasir Depan", pinJson["device_name"].asString)
        assertEquals("refresh-token", refreshJson["refresh_token"].asString)
        assertEquals("refresh-token", logoutJson["refresh_token"].asString)
    }

    private fun json(value: Any) = JsonParser.parseString(gson.toJson(value)).asJsonObject

    private companion object {
        val STORE_RESPONSE_JSON = """
            {
              "tenant": {"id": "tenant-id", "name": "Toko Kita"},
              "users": [
                {
                  "id": "user-id",
                  "name": "Kasir Utama",
                  "role": "CASHIER",
                  "outlet_id": null
                }
              ]
            }
        """.trimIndent()

        val TOKEN_RESPONSE_JSON = """
            {
              "access_token": "access-token",
              "refresh_token": "refresh-token",
              "expires_in": 900
            }
        """.trimIndent()

        val CURRENT_USER_RESPONSE_JSON = """
            {
              "id": "user-id",
              "name": "Admin Toko",
              "role": "ADMIN",
              "tenant_id": "tenant-id",
              "outlet_id": "outlet-id"
            }
        """.trimIndent()
    }
}
