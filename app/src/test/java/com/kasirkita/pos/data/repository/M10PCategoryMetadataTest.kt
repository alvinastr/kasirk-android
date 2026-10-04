package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.core.database.entity.CategoryEntity
import com.kasirkita.pos.core.database.entity.toDomain
import com.kasirkita.pos.data.model.CategoryResponse
import com.kasirkita.pos.data.model.toDomain as categoryResponseToDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class M10PCategoryMetadataTest {

    private val gson = Gson()

    @Test
    fun categoryResponse_mapsToDomainWithTenantId() {
        val response = gson.fromJson(
            """
            {
              "id": "category-id",
              "tenant_id": "tenant-id",
              "name": "Makanan",
              "created_at": "2026-09-23T00:00:00.000Z"
            }
            """.trimIndent(),
            CategoryResponse::class.java,
        )

        val domain = response.categoryResponseToDomain()

        assertEquals("category-id", domain.id)
        assertEquals("tenant-id", domain.tenantId)
        assertEquals("Makanan", domain.name)
    }

    @Test
    fun categoryEntity_toDomain_preservesAllFields() {
        val entity = CategoryEntity(
            id = "cat-1",
            tenantId = "tenant-1",
            name = "Minuman Panas",
        )

        val domain = entity.toDomain()

        assertEquals("cat-1", domain.id)
        assertEquals("tenant-1", domain.tenantId)
        assertEquals("Minuman Panas", domain.name)
        // createdAt defaulted by mapping
        assertEquals("", domain.createdAt)
    }

    @Test
    fun categoryEntity_multipleCategoriesForTenant() {
        val categories = listOf(
            CategoryEntity("cat-1", "tenant-1", "Makanan"),
            CategoryEntity("cat-2", "tenant-1", "Minuman"),
            CategoryEntity("cat-3", "tenant-1", "Snack"),
        )

        val domains = categories.map { it.toDomain() }

        assertEquals(3, domains.size)
        assertEquals("Makanan", domains[0].name)
        assertEquals("Minuman", domains[1].name)
        assertEquals("Snack", domains[2].name)
        assertTrue(domains.all { it.tenantId == "tenant-1" })
    }

    private fun assertTrue(condition: Boolean) {
        assert(condition)
    }
}
