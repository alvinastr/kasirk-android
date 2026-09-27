package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.DailySalesResponse
import com.kasirkita.pos.data.model.SalesSummaryResponse
import com.kasirkita.pos.data.model.TopProductResponse
import com.kasirkita.pos.data.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportResponseJsonTest {

    private val gson = Gson()

    @Test
    fun dailySalesResponse_mapsSnakeCaseContractToDomain() {
        val response = gson.fromJson(DAILY_JSON, DailySalesResponse::class.java)
        val report = response.toDomain()

        assertEquals("2026-09-25", report.date)
        assertNull(report.outletId)
        assertEquals(20, report.transactionCount)
        assertEquals(1_500_000L, report.grossSales)
        assertEquals(50_000L, report.discount)
        assertEquals(1_450_000L, report.netSales)
        assertEquals(159_500L, report.taxCollected)
        assertEquals(1_609_500L, report.totalSales)
        assertEquals(900_000L, report.cogs)
        assertEquals(550_000L, report.grossProfit)
    }

    @Test
    fun salesSummaryResponse_mapsSnakeCaseContractToDomain() {
        val response = gson.fromJson(SUMMARY_JSON, SalesSummaryResponse::class.java)
        val report = response.toDomain()

        assertEquals(100, report.transactionCount)
        assertEquals(10_000_000L, report.grossSales)
        assertEquals(500_000L, report.discount)
        assertEquals(9_500_000L, report.netSales)
        assertEquals(1_045_000L, report.taxCollected)
        assertEquals(10_545_000L, report.totalSales)
        assertEquals(6_000_000L, report.cogs)
        assertEquals(3_500_000L, report.grossProfit)
        assertEquals(105_450L, report.averageTransaction)
    }

    @Test
    fun topProductResponse_mapsSnakeCaseContractToDomain() {
        val response = gson.fromJson(TOP_PRODUCT_JSON, TopProductResponse::class.java)
        val report = response.toDomain()

        assertEquals("uuid", report.productId)
        assertEquals("Kopi Susu", report.productName)
        assertEquals("KS-001", report.sku)
        assertEquals(50, report.quantitySold)
        assertEquals(1_000_000L, report.grossSales)
        assertEquals(500_000L, report.cogs)
        assertEquals(500_000L, report.grossProfit)
    }

    private companion object {
        val DAILY_JSON = """
            {
              "date": "2026-09-25",
              "outlet_id": null,
              "transaction_count": 20,
              "gross_sales": 1500000,
              "discount": 50000,
              "net_sales": 1450000,
              "tax_collected": 159500,
              "total_sales": 1609500,
              "cogs": 900000,
              "gross_profit": 550000
            }
        """.trimIndent()

        val SUMMARY_JSON = """
            {
              "transaction_count": 100,
              "gross_sales": 10000000,
              "discount": 500000,
              "net_sales": 9500000,
              "tax_collected": 1045000,
              "total_sales": 10545000,
              "cogs": 6000000,
              "gross_profit": 3500000,
              "average_transaction": 105450
            }
        """.trimIndent()

        val TOP_PRODUCT_JSON = """
            {
              "product_id": "uuid",
              "product_name": "Kopi Susu",
              "sku": "KS-001",
              "quantity_sold": 50,
              "gross_sales": 1000000,
              "cogs": 500000,
              "gross_profit": 500000
            }
        """.trimIndent()
    }
}
