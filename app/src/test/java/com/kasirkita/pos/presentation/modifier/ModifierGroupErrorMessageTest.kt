package com.kasirkita.pos.presentation.modifier

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ModifierGroupErrorMessageTest {

    @Test
    fun duplicateName_mapsToIndonesianMessage() {
        assertEquals(
            "Nama grup modifier sudah digunakan.",
            modifierGroupCreateErrorMessage(
                httpException(
                    409,
                    "{\"error_code\":\"MODIFIER_GROUP_NAME_EXISTS\",\"message\":\"Modifier group name already exists in this tenant\"}",
                ),
            ),
        )
    }

    @Test
    fun networkFailure_mapsToConnectionMessage() {
        assertEquals(
            "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            modifierGroupCreateErrorMessage(IOException("network unavailable")),
        )
    }

    @Test
    fun unknownFailure_mapsToGenericMessage() {
        assertEquals(
            "Gagal membuat grup modifier",
            modifierGroupCreateErrorMessage(RuntimeException("unexpected")),
        )
    }

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            body.toResponseBody("application/json".toMediaType()),
        ),
    )
}
