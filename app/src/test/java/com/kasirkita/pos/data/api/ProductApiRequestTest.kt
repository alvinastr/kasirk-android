package com.kasirkita.pos.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ProductApiRequestTest {

    private fun apiCapturingUrl(captured: (String) -> Unit): ProductApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                captured(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody())
                    .build()
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProductApi::class.java)
    }

    @Test
    fun getProducts_sendsQueryParamsInSnakeCaseWithCorrectEncoding() = runBlocking {
        var capturedUrl: String? = null
        val api = apiCapturingUrl { capturedUrl = it }

        api.getProducts(
            query = "Kopi Susu",
            categoryId = "cat-789",
            includeModifiers = true,
        )

        val url = capturedUrl!!
        assertTrue(url.startsWith("https://api.example.com/products?"))
        assertTrue(url.contains("q=Kopi+Susu") || url.contains("q=Kopi%20Susu"))
        assertTrue(url.contains("category_id=cat-789"))
        assertTrue(url.contains("include_modifiers=true"))
        assertFalse(url.contains("tenant_id"))
        assertFalse(url.contains("outlet_id"))
    }

    @Test
    fun getProducts_omitsNullOptionalParams() = runBlocking {
        var capturedUrl: String? = null
        val api = apiCapturingUrl { capturedUrl = it }

        api.getProducts(query = null, categoryId = null, includeModifiers = null)

        val url = capturedUrl!!
        assertEquals("https://api.example.com/products", url)
        assertFalse(url.contains("q="))
        assertFalse(url.contains("category_id="))
        assertFalse(url.contains("include_modifiers="))
    }

    @Test
    fun getProducts_includeModifiersFalse_isSerializedNotDropped() = runBlocking {
        var capturedUrl: String? = null
        val api = apiCapturingUrl { capturedUrl = it }

        api.getProducts(query = null, categoryId = null, includeModifiers = false)

        assertTrue(capturedUrl!!.contains("include_modifiers=false"))
    }
}
