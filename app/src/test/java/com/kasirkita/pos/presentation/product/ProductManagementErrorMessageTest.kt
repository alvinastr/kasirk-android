package com.kasirkita.pos.presentation.product

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ProductManagementErrorMessageTest {

    @Test
    fun skuAlreadyExists_mapsToReadableMessage() {
        assertEquals(
            "SKU sudah digunakan. Gunakan SKU lain.",
            productManagementErrorMessage(
                httpException(409, "{\"message\":\"SKU_ALREADY_EXISTS\"}"),
            ),
        )
    }

    @Test
    fun categoryNotFound_mapsToReadableMessage() {
        assertEquals(
            "Kategori tidak ditemukan. Pilih kategori lain atau gunakan Tanpa kategori.",
            productManagementErrorMessage(
                httpException(404, "{\"message\":\"CATEGORY_NOT_FOUND\"}"),
            ),
        )
    }

    @Test
    fun validationError_mapsToReadableMessage() {
        assertEquals(
            "Data produk belum valid. Periksa kembali data yang wajib diisi.",
            productManagementErrorMessage(
                httpException(400, "{\"message\":[\"price must be an integer\"]}"),
            ),
        )
    }

    @Test
    fun productNotFound_mapsToReadableMessage() {
        assertEquals(
            "Produk tidak ditemukan. Daftar produk mungkin sudah berubah.",
            productManagementErrorMessage(
                httpException(404, "{\"message\":\"PRODUCT_NOT_FOUND\"}"),
            ),
        )
    }

    @Test
    fun unauthorized_mapsToExpiredSessionMessage() {
        assertEquals(
            "Sesi login berakhir. Silakan login kembali.",
            productManagementErrorMessage(
                httpException(401, "{\"message\":\"Unauthorized\"}"),
            ),
        )
    }

    @Test
    fun forbidden_mapsToManagementPermissionMessage() {
        assertEquals(
            "Anda tidak memiliki izin untuk mengelola produk.",
            productManagementErrorMessage(
                httpException(403, "{\"message\":\"Forbidden\"}"),
            ),
        )
    }

    @Test
    fun networkFailure_mapsToConnectionMessage() {
        assertEquals(
            "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            productManagementErrorMessage(IOException("network unavailable")),
        )
    }

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            body.toResponseBody("application/json".toMediaType()),
        ),
    )
}
