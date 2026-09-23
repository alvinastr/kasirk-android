package com.kasirkita.pos.presentation.stock

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class StockAdjustmentErrorMessageTest {

    @Test
    fun stockTrackingDisabled_mapsToReadableMessage() {
        assertEquals(
            "Stok produk ini tidak dikelola.",
            stockAdjustmentErrorMessage(
                httpException(409, "{\"error_code\":\"STOCK_TRACKING_DISABLED\"}"),
            ),
        )
    }

    @Test
    fun productNotFound_mapsToReadableMessage() {
        assertEquals(
            "Produk tidak ditemukan. Daftar produk mungkin sudah berubah.",
            stockAdjustmentErrorMessage(
                httpException(404, "{\"message\":\"Product not found\"}"),
            ),
        )
    }

    @Test
    fun insufficientStock_mapsToReadableMessage() {
        assertEquals(
            "Stok tidak mencukupi untuk dikurangi sebanyak itu.",
            stockAdjustmentErrorMessage(
                httpException(409, "{\"message\":\"Insufficient stock\"}"),
            ),
        )
    }

    @Test
    fun unauthorized_mapsToExpiredSessionMessage() {
        assertEquals(
            "Sesi login berakhir. Silakan login kembali.",
            stockAdjustmentErrorMessage(httpException(401, "{}")),
        )
    }

    @Test
    fun forbidden_mapsToPermissionMessage() {
        assertEquals(
            "Anda tidak memiliki izin untuk menyesuaikan stok.",
            stockAdjustmentErrorMessage(httpException(403, "{}")),
        )
    }

    @Test
    fun networkFailure_mapsToConnectionMessage() {
        assertEquals(
            "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            stockAdjustmentErrorMessage(IOException("network unavailable")),
        )
    }

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            body.toResponseBody("application/json".toMediaType()),
        ),
    )
}
