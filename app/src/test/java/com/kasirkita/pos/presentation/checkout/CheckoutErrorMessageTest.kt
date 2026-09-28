package com.kasirkita.pos.presentation.checkout

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CheckoutErrorMessageTest {

    @Test
    fun insufficientStock_mapsToCashierFriendlyMessage() {
        val exception = HttpException(
            Response.error<Any>(
                409,
                "{\"error_code\":\"INSUFFICIENT_STOCK\"}"
                    .toResponseBody("application/json".toMediaType()),
            ),
        )

        assertEquals(
            "Stok tidak mencukupi. Kurangi jumlah produk di cart lalu coba lagi.",
            checkoutErrorMessage(exception),
        )
    }
}
