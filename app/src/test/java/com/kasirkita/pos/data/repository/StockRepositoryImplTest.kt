package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.StockApi
import com.kasirkita.pos.data.model.CreateStockAdjustmentRequest
import com.kasirkita.pos.data.model.StockProductResponse
import com.kasirkita.pos.data.model.StockResponse
import com.kasirkita.pos.domain.model.StockAdjustmentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StockRepositoryImplTest {

    @Test
    fun getStocks_mapsApiResponseToDomain() = runBlocking {
        val api = FakeStockApi(stocksResponse = listOf(stockResponse(quantity = 40)))
        val repository = StockRepositoryImpl(api)

        val stocks = repository.getStocks(OUTLET_ID).getOrThrow()

        assertEquals(1, stocks.size)
        assertEquals(PRODUCT_ID, stocks.single().productId)
        assertEquals(40, stocks.single().quantity)
        assertEquals("Kopi Hitam", stocks.single().productName)
        assertEquals(OUTLET_ID, api.lastOutletId)
    }

    @Test
    fun createAdjustment_sendsRequestAndMapsUpdatedStock() = runBlocking {
        val api = FakeStockApi(
            adjustmentResponse = stockResponse(
                quantity = 45,
                products = null,
            ),
        )
        val repository = StockRepositoryImpl(api)

        val stock = repository.createAdjustment(
            outletId = OUTLET_ID,
            productId = PRODUCT_ID,
            adjustmentType = StockAdjustmentType.ADD,
            quantity = 5,
            reason = "Stok masuk",
        ).getOrThrow()

        assertEquals(45, stock.quantity)
        assertNull(stock.productName)
        assertEquals(
            CreateStockAdjustmentRequest(
                outletId = OUTLET_ID,
                productId = PRODUCT_ID,
                adjustmentType = StockAdjustmentType.ADD,
                quantity = 5,
                reason = "Stok masuk",
            ),
            api.lastRequest,
        )
    }

    @Test
    fun createAdjustment_whenApiFails_returnsSameFailure() = runBlocking {
        val expected = IOException("network unavailable")
        val repository = StockRepositoryImpl(
            FakeStockApi(adjustmentFailure = expected),
        )

        val result = repository.createAdjustment(
            outletId = OUTLET_ID,
            productId = PRODUCT_ID,
            adjustmentType = StockAdjustmentType.DEDUCT,
            quantity = 2,
            reason = null,
        )

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    private class FakeStockApi(
        private val stocksResponse: List<StockResponse> = emptyList(),
        private val adjustmentResponse: StockResponse? = null,
        private val adjustmentFailure: Throwable? = null,
    ) : StockApi {
        var lastOutletId: String? = null
        var lastRequest: CreateStockAdjustmentRequest? = null

        override suspend fun getStocks(outletId: String): List<StockResponse> {
            lastOutletId = outletId
            return stocksResponse
        }

        override suspend fun createAdjustment(
            request: CreateStockAdjustmentRequest,
        ): StockResponse {
            lastRequest = request
            adjustmentFailure?.let { throwable -> throw throwable }
            return requireNotNull(adjustmentResponse)
        }
    }

    private companion object {
        const val OUTLET_ID = "outlet-id"
        const val PRODUCT_ID = "product-id"

        fun stockResponse(
            quantity: Int,
            products: StockProductResponse? = StockProductResponse(
                id = PRODUCT_ID,
                name = "Kopi Hitam",
                sku = "KOPI001",
                trackStock = true,
            ),
        ) = StockResponse(
            id = "stock-id",
            outletId = OUTLET_ID,
            productId = PRODUCT_ID,
            stock = quantity,
            updatedAt = "2026-09-23T00:00:00.000Z",
            products = products,
        )
    }
}
