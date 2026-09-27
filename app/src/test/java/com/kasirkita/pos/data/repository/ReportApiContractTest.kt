package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.ReportApi
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Query

class ReportApiContractTest {

    @Test
    fun endpoints_matchBackendPaths() {
        assertEquals("reports/sales/daily", getPath("getDailySales"))
        assertEquals("reports/sales/summary", getPath("getSalesSummary"))
        assertEquals("reports/products/top", getPath("getTopProducts"))
    }

    @Test
    fun dailyQuery_usesDateAndOptionalOutletId() {
        assertEquals(
            listOf("date", "outlet_id"),
            queryNames("getDailySales"),
        )
    }

    @Test
    fun summaryQuery_usesExclusiveRangeAndOptionalOutletId() {
        assertEquals(
            listOf("start_date", "end_date", "outlet_id"),
            queryNames("getSalesSummary"),
        )
    }

    @Test
    fun topProductsQuery_includesLimitAndOptionalOutletId() {
        assertEquals(
            listOf("start_date", "end_date", "limit", "outlet_id"),
            queryNames("getTopProducts"),
        )
    }

    private fun getPath(methodName: String): String = requireNotNull(
        method(methodName).getAnnotation(GET::class.java),
    ).value

    private fun queryNames(methodName: String): List<String> = method(methodName)
        .parameterAnnotations
        .mapNotNull { annotations ->
            annotations.filterIsInstance<Query>().singleOrNull()?.value
        }

    private fun method(name: String) = ReportApi::class.java.declaredMethods.single {
        it.name == name
    }
}
