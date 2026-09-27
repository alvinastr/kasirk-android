package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.ReportApi
import com.kasirkita.pos.data.model.DailySalesResponse
import com.kasirkita.pos.data.model.SalesSummaryResponse
import com.kasirkita.pos.data.model.TopProductResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReportRepositoryImplTest {

    @Test
    fun getDailySales_mapsResponseAndForwardsOptionalOutlet() = runBlocking {
        val api = FakeReportApi()
        val repository = ReportRepositoryImpl(api)

        val report = repository.getDailySales(
            date = REPORT_DATE,
            outletId = null,
        ).getOrThrow()

        assertEquals(REPORT_DATE, api.dailyDate)
        assertNull(api.dailyOutletId)
        assertEquals(20, report.transactionCount)
        assertEquals(1_609_500L, report.totalSales)
    }

    @Test
    fun getSalesSummary_mapsResponseAndForwardsExclusiveDateRange() = runBlocking {
        val api = FakeReportApi()
        val repository = ReportRepositoryImpl(api)

        val report = repository.getSalesSummary(
            startDate = START_DATE,
            endDate = END_DATE,
            outletId = OUTLET_ID,
        ).getOrThrow()

        assertEquals(START_DATE, api.summaryStartDate)
        assertEquals(END_DATE, api.summaryEndDate)
        assertEquals(OUTLET_ID, api.summaryOutletId)
        assertEquals(105_450L, report.averageTransaction)
    }

    @Test
    fun getTopProducts_mapsResponseAndForwardsLimit() = runBlocking {
        val api = FakeReportApi()
        val repository = ReportRepositoryImpl(api)

        val products = repository.getTopProducts(
            startDate = START_DATE,
            endDate = END_DATE,
            limit = 25,
            outletId = OUTLET_ID,
        ).getOrThrow()

        assertEquals(START_DATE, api.topStartDate)
        assertEquals(END_DATE, api.topEndDate)
        assertEquals(25, api.topLimit)
        assertEquals(OUTLET_ID, api.topOutletId)
        assertEquals("uuid", products.single().productId)
        assertEquals(50, products.single().quantitySold)
    }

    @Test
    fun apiFailure_isReturnedWithoutReplacement() = runBlocking {
        val expected = IOException("network unavailable")
        val repository = ReportRepositoryImpl(FakeReportApi(failure = expected))

        val result = repository.getDailySales(REPORT_DATE)

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    private class FakeReportApi(
        private val failure: Throwable? = null,
    ) : ReportApi {
        var dailyDate: String? = null
        var dailyOutletId: String? = null
        var summaryStartDate: String? = null
        var summaryEndDate: String? = null
        var summaryOutletId: String? = null
        var topStartDate: String? = null
        var topEndDate: String? = null
        var topLimit: Int? = null
        var topOutletId: String? = null

        override suspend fun getDailySales(
            date: String,
            outletId: String?,
        ): DailySalesResponse {
            dailyDate = date
            dailyOutletId = outletId
            failure?.let { throw it }
            return dailyResponse()
        }

        override suspend fun getSalesSummary(
            startDate: String,
            endDate: String,
            outletId: String?,
        ): SalesSummaryResponse {
            summaryStartDate = startDate
            summaryEndDate = endDate
            summaryOutletId = outletId
            failure?.let { throw it }
            return summaryResponse()
        }

        override suspend fun getTopProducts(
            startDate: String,
            endDate: String,
            limit: Int,
            outletId: String?,
        ): List<TopProductResponse> {
            topStartDate = startDate
            topEndDate = endDate
            topLimit = limit
            topOutletId = outletId
            failure?.let { throw it }
            return listOf(topProductResponse())
        }
    }

    private companion object {
        const val REPORT_DATE = "2026-09-25"
        const val START_DATE = "2026-09-01"
        const val END_DATE = "2026-10-01"
        const val OUTLET_ID = "outlet-id"

        fun dailyResponse() = DailySalesResponse(
            date = REPORT_DATE,
            outletId = null,
            transactionCount = 20,
            grossSales = 1_500_000L,
            discount = 50_000L,
            netSales = 1_450_000L,
            taxCollected = 159_500L,
            totalSales = 1_609_500L,
            cogs = 900_000L,
            grossProfit = 550_000L,
        )

        fun summaryResponse() = SalesSummaryResponse(
            transactionCount = 100,
            grossSales = 10_000_000L,
            discount = 500_000L,
            netSales = 9_500_000L,
            taxCollected = 1_045_000L,
            totalSales = 10_545_000L,
            cogs = 6_000_000L,
            grossProfit = 3_500_000L,
            averageTransaction = 105_450L,
        )

        fun topProductResponse() = TopProductResponse(
            productId = "uuid",
            productName = "Kopi Susu",
            sku = "KS-001",
            quantitySold = 50,
            grossSales = 1_000_000L,
            cogs = 500_000L,
            grossProfit = 500_000L,
        )
    }
}
