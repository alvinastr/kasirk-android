package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.AuthV2Api
import com.kasirkita.pos.data.model.LogoutRequest
import com.kasirkita.pos.data.model.PinLoginRequest
import com.kasirkita.pos.data.model.RefreshTokenRequest
import com.kasirkita.pos.data.model.StoreResolveRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

class AuthV2ApiContractTest {

    @Test
    fun postEndpoints_matchBackendPathsAndRequestTypes() {
        assertPost("resolveStore", "auth/v2/store/resolve", StoreResolveRequest::class.java)
        assertPost("pinLogin", "auth/v2/pin/login", PinLoginRequest::class.java)
        assertPost("refreshToken", "auth/v2/refresh", RefreshTokenRequest::class.java)
        assertPost("logout", "auth/v2/logout", LogoutRequest::class.java)
    }

    @Test
    fun currentUserEndpoint_matchesBackendPath() {
        val annotation = method("getCurrentUser").getAnnotation(GET::class.java)

        assertEquals("auth/v2/me", requireNotNull(annotation).value)
    }

    private fun assertPost(
        methodName: String,
        expectedPath: String,
        expectedRequestType: Class<*>,
    ) {
        val method = method(methodName)

        assertEquals(expectedPath, requireNotNull(method.getAnnotation(POST::class.java)).value)
        assertEquals(expectedRequestType, method.parameterTypes.first())
        assertNotNull(method.parameterAnnotations.first().filterIsInstance<Body>().singleOrNull())
    }

    private fun method(name: String) = AuthV2Api::class.java.declaredMethods.single {
        it.name == name
    }
}
