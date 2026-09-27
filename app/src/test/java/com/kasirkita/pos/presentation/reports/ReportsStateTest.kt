package com.kasirkita.pos.presentation.reports

import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.TopProductReport
import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsStateTest {

    @Test
    fun reportsWithTopProducts_returnsSuccess() {
        val topProduct = TopProductReport(
            productId = "product-id",
            productName = "Kopi Susu",
            sku = "KS-001",
            quantitySold = 50,
            grossSales = 1_000_000L,
            cogs = 500_000L,
            grossProfit = 500_000L,
        )

        val state = reportsState(
            dailyResult = Result.success(dailySales()),
            topProductsResult = Result.success(listOf(topProduct)),
        ) as ReportsState.Success

        assertEquals(1_609_500L, state.dailySales.totalSales)
        assertEquals("KS-001", state.topProducts.single().sku)
    }

    @Test
    fun reportsWithoutTopProducts_returnsEmptyWithDailyMetrics() {
        val dailySales = dailySales()

        val state = reportsState(
            dailyResult = Result.success(dailySales),
            topProductsResult = Result.success(emptyList()),
        ) as ReportsState.Empty

        assertSame(dailySales, state.dailySales)
    }

    @Test
    fun networkFailure_returnsReadableError() {
        val state = reportsState(
            dailyResult = Result.failure(IOException("offline")),
            topProductsResult = Result.success(emptyList()),
        ) as ReportsState.Error

        assertTrue(state.message.contains("Periksa koneksi"))
    }

    @Test
    fun dateRange_usesExclusiveNextDayEndDate() {
        assertEquals(
            "2026-09-25" to "2026-09-26",
            reportsDateRange(LocalDate.of(2026, 9, 25)),
        )
    }

    private fun dailySales() = DailySalesReport(
        date = "2026-09-25",
        outletId = "outlet-id",
        transactionCount = 20,
        grossSales = 1_500_000L,
        discount = 50_000L,
        netSales = 1_450_000L,
        taxCollected = 159_500L,
        totalSales = 1_609_500L,
        cogs = 900_000L,
        grossProfit = 550_000L,
    )
}
