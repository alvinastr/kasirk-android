package com.kasirkita.pos.presentation.stock

import com.kasirkita.pos.domain.model.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StockAdjustmentStateTest {

    @Test
    fun successfulLoad_exposesCurrentStockAndMarksLoaded() {
        val state = stockStateAfterLoad(
            currentState = StockAdjustmentState(isLoadingStock = true),
            result = Result.success(listOf(stock(quantity = 40))),
        )

        assertTrue(state.hasLoadedStocks)
        assertFalse(state.isLoadingStock)
        assertEquals(40, state.currentStock(PRODUCT_ID))
        assertEquals(0, state.currentStock("missing-product"))
        assertNull(state.stockLoadError)
    }

    @Test
    fun failedLoad_keepsStateRetryableWithReadableError() {
        val state = stockStateAfterLoad(
            currentState = StockAdjustmentState(isLoadingStock = true),
            result = Result.failure(IOException("network unavailable")),
        )

        assertFalse(state.hasLoadedStocks)
        assertFalse(state.isLoadingStock)
        assertEquals(
            "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            state.stockLoadError,
        )
    }

    @Test
    fun successfulAdjustment_replacesStockAndMarksSuccess() {
        val state = stockStateAfterAdjustment(
            currentState = StockAdjustmentState(
                stocks = listOf(stock(quantity = 40)),
                hasLoadedStocks = true,
                isSubmitting = true,
            ),
            result = Result.success(stock(quantity = 45)),
        )

        assertFalse(state.isSubmitting)
        assertTrue(state.adjustmentSucceeded)
        assertEquals(45, state.currentStock(PRODUCT_ID))
    }

    @Test
    fun failedAdjustment_stopsSubmittingAndExposesReadableError() {
        val state = stockStateAfterAdjustment(
            currentState = StockAdjustmentState(
                stocks = listOf(stock(quantity = 40)),
                hasLoadedStocks = true,
                isSubmitting = true,
            ),
            result = Result.failure(IOException("network unavailable")),
        )

        assertFalse(state.isSubmitting)
        assertFalse(state.adjustmentSucceeded)
        assertEquals(40, state.currentStock(PRODUCT_ID))
        assertEquals(
            "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi.",
            state.submitError,
        )
    }

    private fun stock(quantity: Int) = Stock(
        id = "stock-id",
        outletId = "outlet-id",
        productId = PRODUCT_ID,
        quantity = quantity,
        updatedAt = "2026-09-23T00:00:00.000Z",
    )

    private companion object {
        const val PRODUCT_ID = "product-id"
    }
}
