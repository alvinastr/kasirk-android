package com.kasirkita.pos.presentation.authv2

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AuthV2ErrorMessageTest {

    @Test
    fun mapsStoreNotFound() {
        assertEquals(
            "Kode toko tidak ditemukan. Periksa kode lalu coba lagi.",
            authV2ErrorMessage(
                httpException(404, "STORE_NOT_FOUND"),
                AuthV2Action.RESOLVE_STORE,
            ),
        )
    }

    @Test
    fun mapsInvalidCredentials() {
        assertEquals(
            "PIN salah atau pengguna tidak dapat masuk.",
            authV2ErrorMessage(
                httpException(401, "INVALID_CREDENTIALS"),
                AuthV2Action.PIN_LOGIN,
            ),
        )
    }

    @Test
    fun mapsPinLocked() {
        assertEquals(
            "Terlalu banyak percobaan PIN. Tunggu beberapa saat lalu coba lagi.",
            authV2ErrorMessage(
                httpException(423, "PIN_LOCKED"),
                AuthV2Action.PIN_LOGIN,
            ),
        )
    }

    @Test
    fun mapsNetworkFailure() {
        assertEquals(
            "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            authV2ErrorMessage(
                IOException("network unavailable"),
                AuthV2Action.PIN_LOGIN,
            ),
        )
    }

    private fun httpException(code: Int, errorCode: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            "{\"error_code\":\"$errorCode\"}"
                .toResponseBody("application/json".toMediaType()),
        ),
    )
}
