package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.CategoryResponse
import com.kasirkita.pos.data.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMappingTest {

    @Test
    fun categoryResponse_mapsBackendFieldsToDomain() {
        val response = Gson().fromJson(
            """
            {
              "id": "category-id",
              "tenant_id": "tenant-id",
              "name": "Minuman",
              "created_at": "2026-09-23T00:00:00.000Z"
            }
            """.trimIndent(),
            CategoryResponse::class.java,
        )

        val category = response.toDomain()

        assertEquals("category-id", category.id)
        assertEquals("tenant-id", category.tenantId)
        assertEquals("Minuman", category.name)
        assertEquals("2026-09-23T00:00:00.000Z", category.createdAt)
    }
}
