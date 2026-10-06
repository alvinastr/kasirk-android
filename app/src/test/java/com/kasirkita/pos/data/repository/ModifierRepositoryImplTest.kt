package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kasirkita.pos.core.database.dao.ModifierGroupDao
import com.kasirkita.pos.core.database.dao.ModifierOptionDao
import com.kasirkita.pos.core.database.dao.ProductModifierGroupDao
import com.kasirkita.pos.core.database.entity.ModifierGroupEntity
import com.kasirkita.pos.core.database.entity.ModifierOptionEntity
import com.kasirkita.pos.core.database.entity.ProductModifierGroupEntity
import com.kasirkita.pos.core.datastore.AuthSessionDataStoreTestHelper
import com.kasirkita.pos.data.api.ModifierGroupApi
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.local.ModifierLocalDataSource
import com.kasirkita.pos.data.model.AssignModifierGroupRequest
import com.kasirkita.pos.data.model.CreateModifierGroupRequest
import com.kasirkita.pos.data.model.CreateModifierOptionRequest
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.ModifierGroupResponse
import com.kasirkita.pos.data.model.ModifierOptionResponse
import com.kasirkita.pos.data.model.ProductModifierGroupsResponse
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.data.model.UpdateModifierGroupRequest
import com.kasirkita.pos.data.model.UpdateModifierOptionRequest
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.SelectionMode
import com.kasirkita.pos.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ModifierRepositoryImplTest {
    private val gson = Gson()

    @Test
    fun productModifierGroupsResponse_backendWrapperShape_deserializesContractFields() {
        val response = gson.fromJson(productModifierGroupsJson(), ProductModifierGroupsResponse::class.java)

        assertEquals("product-1", response.productId)
        val group = response.modifierGroups.single()
        assertEquals("group-1", group.id)
        assertEquals("ICE/HOT", group.name)
        assertEquals(true, group.required)
        assertEquals("SINGLE", group.selectionType)
        assertEquals(0, group.displayOrder)
        assertEquals(emptyList<ModifierOptionResponse>(), group.options)
    }

    @Test
    fun getProductModifierGroups_extractsAssignmentsFromBackendWrapper() = runBlocking {
        val api = FakeProductApi(
            productModifierGroupsResponse = gson.fromJson(
                productModifierGroupsJson(),
                ProductModifierGroupsResponse::class.java,
            ),
        )
        val sessionStore = AuthSessionDataStoreTestHelper.createTestStore()
        sessionStore.saveSession(session())
        val repository = ModifierRepositoryImpl(
            modifierGroupApi = FakeModifierGroupApi(),
            productApi = api,
            localDataSource = ModifierLocalDataSource(
                FakeModifierGroupDao(),
                FakeModifierOptionDao(),
                FakeProductModifierGroupDao(),
            ),
            authSessionDataStore = sessionStore,
        )

        val assignments = repository.getProductModifierGroups("product-1").getOrThrow()

        assertEquals("product-1", api.lastProductModifierGroupsProductId)
        val assignment = assignments.single()
        assertEquals("product-1", assignment.productId)
        assertEquals("group-1", assignment.groupId)
        assertEquals(true, assignment.required)
        assertEquals(SelectionMode.SINGLE, assignment.selectionMode)
        assertEquals(0, assignment.displayOrder)
    }

    private fun productModifierGroupsJson(): String =
        """
        {
          "product_id": "product-1",
          "modifier_groups": [
            {
              "id": "group-1",
              "name": "ICE/HOT",
              "required": true,
              "selection_type": "SINGLE",
              "display_order": 0,
              "options": []
            }
          ]
        }
        """.trimIndent()

    private fun session() = AuthSession(
        userId = "user-id",
        userName = "Admin",
        tenantId = "tenant-id",
        role = UserRole.ADMIN,
        outletId = null,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = 4_102_444_800_000L,
        deviceId = "device-id",
    )

    private class FakeProductApi(
        private val productModifierGroupsResponse: ProductModifierGroupsResponse,
    ) : ProductApi {
        var lastProductModifierGroupsProductId: String? = null

        override suspend fun getProducts(
            query: String?,
            categoryId: String?,
            includeModifiers: Boolean?,
        ): List<ProductResponse> = error("Not used")

        override suspend fun createProduct(request: CreateProductRequest): ProductResponse = error("Not used")

        override suspend fun updateProduct(productId: String, request: JsonObject): ProductResponse = error("Not used")

        override suspend fun getProductModifierGroups(productId: String): ProductModifierGroupsResponse {
            lastProductModifierGroupsProductId = productId
            return productModifierGroupsResponse
        }

        override suspend fun assignModifierGroup(
            productId: String,
            request: AssignModifierGroupRequest,
        ): ProductModifierGroupsResponse = error("Not used")

        override suspend fun updateModifierGroupAssignment(
            productId: String,
            groupId: String,
            request: AssignModifierGroupRequest,
        ): ProductModifierGroupsResponse = error("Not used")

        override suspend fun removeModifierGroup(productId: String, groupId: String) = Unit

        override suspend fun replaceModifierGroups(
            productId: String,
            request: JsonObject,
        ): ProductModifierGroupsResponse = error("Not used")
    }

    private class FakeModifierGroupApi : ModifierGroupApi {
        override suspend fun getModifierGroups(includeInactive: Boolean?): List<ModifierGroupResponse> = error("Not used")
        override suspend fun getModifierGroup(groupId: String): ModifierGroupResponse = error("Not used")
        override suspend fun createModifierGroup(request: CreateModifierGroupRequest): ModifierGroupResponse = error("Not used")
        override suspend fun updateModifierGroup(groupId: String, request: UpdateModifierGroupRequest): ModifierGroupResponse = error("Not used")
        override suspend fun deleteModifierGroup(groupId: String) = Unit
        override suspend fun createModifierOption(groupId: String, request: CreateModifierOptionRequest): ModifierOptionResponse = error("Not used")
        override suspend fun updateModifierOption(groupId: String, optionId: String, request: UpdateModifierOptionRequest): ModifierOptionResponse = error("Not used")
        override suspend fun deleteModifierOption(groupId: String, optionId: String) = Unit
    }

    private class FakeModifierGroupDao : ModifierGroupDao {
        override suspend fun getModifierGroups(tenantId: String): List<ModifierGroupEntity> = emptyList()
        override suspend fun getModifierGroupsForProduct(tenantId: String, productId: String): List<ModifierGroupEntity> = emptyList()
        override suspend fun insertModifierGroups(groups: List<ModifierGroupEntity>) = Unit
        override suspend fun deleteAll(tenantId: String) = Unit
    }

    private class FakeModifierOptionDao : ModifierOptionDao {
        override suspend fun getOptionsForGroup(tenantId: String, modifierGroupId: String): List<ModifierOptionEntity> = emptyList()
        override suspend fun getOptionsForGroups(tenantId: String, modifierGroupIds: List<String>): List<ModifierOptionEntity> = emptyList()
        override suspend fun insertOptions(options: List<ModifierOptionEntity>) = Unit
        override suspend fun deleteAllForTenant(tenantId: String) = Unit
    }

    private class FakeProductModifierGroupDao : ProductModifierGroupDao {
        override suspend fun getAssignmentsForProduct(tenantId: String, productId: String): List<ProductModifierGroupEntity> = emptyList()
        override suspend fun getAssignmentsForProducts(tenantId: String, productIds: List<String>): List<ProductModifierGroupEntity> = emptyList()
        override suspend fun insertAssignments(assignments: List<ProductModifierGroupEntity>) = Unit
        override suspend fun deleteAllForTenant(tenantId: String) = Unit
    }
}
