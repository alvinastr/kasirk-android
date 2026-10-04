package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.database.entity.CategoryEntity
import com.kasirkita.pos.core.database.entity.ModifierGroupEntity
import com.kasirkita.pos.core.database.entity.ModifierOptionEntity
import com.kasirkita.pos.core.database.entity.ProductEntity
import com.kasirkita.pos.core.database.entity.ProductModifierGroupEntity
import com.kasirkita.pos.core.database.entity.toDomain
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.local.CategoryLocalDataSource
import com.kasirkita.pos.data.local.ModifierLocalDataSource
import com.kasirkita.pos.data.local.ProductLocalDataSource
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.data.model.UpdateProductRequest
import com.kasirkita.pos.data.model.toJsonObject
import com.kasirkita.pos.data.model.toDomain as modifierGroupToDomain
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.ProductRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productApi: ProductApi,
    private val localDataSource: ProductLocalDataSource,
    private val categoryLocalDataSource: CategoryLocalDataSource,
    private val modifierLocalDataSource: ModifierLocalDataSource,
    private val authSessionDataStore: AuthSessionDataStore,
) : ProductRepository {

    override suspend fun getProducts(
        query: String?,
        categoryId: String?,
        includeModifiers: Boolean?
    ): Result<List<Product>> = runCatching {
        val tenantId = requireTenantId()
        val isFiltered = query != null || categoryId != null || includeModifiers != null

        if (isFiltered) {
            fetchAndCacheProducts(tenantId, query, categoryId, includeModifiers, cache = false)
        } else {
            val cachedProducts = localDataSource.getProducts(tenantId)
            if (cachedProducts.isNotEmpty()) {
                cachedProducts.map { it.toDomain(modifierLocalDataSource) }
            } else {
                fetchAndCacheProducts(tenantId, query, categoryId, includeModifiers = true, cache = true)
            }
        }
    }

    override suspend fun refreshProducts(
        query: String?,
        categoryId: String?,
        includeModifiers: Boolean?
    ): Result<List<Product>> = runCatching {
        val canonicalFullRefresh = query == null && categoryId == null
        fetchAndCacheProducts(
            tenantId = requireTenantId(),
            query = query,
            categoryId = categoryId,
            includeModifiers = if (canonicalFullRefresh) includeModifiers ?: true else includeModifiers,
            cache = canonicalFullRefresh,
        )
    }

    override suspend fun createProduct(
        name: String,
        sku: String,
        categoryId: String?,
        price: Long,
        cost: Long,
        minimumStock: Int,
        trackStock: Boolean,
    ): Result<Product> = runCatching {
        val tenantId = requireTenantId()
        productApi.createProduct(
            CreateProductRequest(
                name = name,
                sku = sku,
                categoryId = categoryId,
                price = price,
                cost = cost,
                minimumStock = minimumStock,
                trackStock = trackStock,
            ),
        ).also { response -> response.toEntity().requireTenant(tenantId) }.toDomain()
    }

    override suspend fun updateProduct(
        productId: String,
        name: String?,
        sku: String?,
        categoryId: String?,
        categoryIdChanged: Boolean,
        price: Long?,
        cost: Long?,
        minimumStock: Int?,
        trackStock: Boolean?,
    ): Result<Product> = runCatching {
        val tenantId = requireTenantId()
        productApi.updateProduct(
            productId = productId,
            request = UpdateProductRequest(
                name = name,
                sku = sku,
                categoryId = categoryId,
                categoryIdChanged = categoryIdChanged,
                price = price,
                cost = cost,
                minimumStock = minimumStock,
                trackStock = trackStock,
            ).toJsonObject(),
        ).also { response -> response.toEntity().requireTenant(tenantId) }.toDomain()
    }

    private suspend fun fetchAndCacheProducts(
        tenantId: String,
        query: String? = null,
        categoryId: String? = null,
        includeModifiers: Boolean? = null,
        cache: Boolean = false,
    ): List<Product> {
        val actualIncludeModifiers = includeModifiers ?: true
        val remoteProducts = productApi.getProducts(
            query = query,
            categoryId = categoryId,
            includeModifiers = actualIncludeModifiers,
        )
        val entities = remoteProducts.map { response ->
            response.toEntity(actualIncludeModifiers).requireTenant(tenantId)
        }

        if (cache) {
            localDataSource.saveProducts(tenantId, entities)
            cacheCategoryAndModifierMetadata(tenantId, remoteProducts)
        }

        return remoteProducts.map { response ->
            response.toDomain(actualIncludeModifiers)
        }
    }

    private suspend fun cacheCategoryAndModifierMetadata(
        tenantId: String,
        remoteProducts: List<ProductResponse>,
    ) {
        val categories = mutableListOf<CategoryEntity>()
        val modifierGroups = mutableListOf<ModifierGroupEntity>()
        val modifierOptions = mutableListOf<ModifierOptionEntity>()
        val productModifierGroups = mutableListOf<ProductModifierGroupEntity>()

        remoteProducts.forEach { response ->
            response.category?.let { category ->
                categories += CategoryEntity(
                    id = category.id,
                    tenantId = tenantId,
                    name = category.name,
                )
            }

            response.modifierGroups?.forEach { group ->
                modifierGroups += ModifierGroupEntity(
                    id = group.id,
                    tenantId = tenantId,
                    name = group.name,
                    isActive = group.isActive,
                )
                group.options.forEach { option ->
                    modifierOptions += ModifierOptionEntity(
                        id = option.id,
                        tenantId = tenantId,
                        modifierGroupId = group.id,
                        name = option.name,
                        priceDelta = option.priceDelta,
                        isActive = option.isActive,
                        displayOrder = option.displayOrder,
                    )
                }
                productModifierGroups += ProductModifierGroupEntity(
                    tenantId = tenantId,
                    productId = response.id,
                    modifierGroupId = group.id,
                    required = group.required,
                    selectionType = group.selectionType,
                    displayOrder = group.displayOrder,
                )
            }
        }

        if (categories.isNotEmpty()) {
            categoryLocalDataSource.saveCategories(tenantId, categories.distinctBy { it.id })
        }
        val metadataLoaded = remoteProducts.all { it.modifierGroups != null }
        if (metadataLoaded) {
            modifierLocalDataSource.replaceModifierMetadata(
                tenantId = tenantId,
                groups = modifierGroups.distinctBy { it.id },
                options = modifierOptions.distinctBy { it.id },
                assignments = productModifierGroups,
            )
        }
    }

    private suspend fun requireTenantId(): String = authSessionDataStore
        .getSession()
        ?.tenantId
        ?: error("Authenticated session is required")
}

private fun ProductEntity.requireTenant(tenantId: String): ProductEntity = apply {
    require(this.tenantId == tenantId) {
        "Product response belongs to a different tenant"
    }
}

internal fun ProductResponse.toEntity(
    modifierMetadataLoaded: Boolean = modifierGroups != null,
): ProductEntity = ProductEntity(
    id = id,
    tenantId = tenantId,
    categoryId = categoryId,
    name = name,
    sku = sku,
    price = price,
    cost = cost,
    minimumStock = minimumStock,
    trackStock = trackStock,
    isActive = isActive,
    createdAt = createdAt,
    modifierMetadataLoaded = modifierMetadataLoaded,
)

internal fun ProductResponse.toDomain(
    modifierMetadataLoaded: Boolean = modifierGroups != null,
): Product = Product(
    id = id,
    tenantId = tenantId,
    categoryId = categoryId,
    name = name,
    sku = sku,
    price = price,
    cost = cost,
    minimumStock = minimumStock,
    trackStock = trackStock,
    isActive = isActive,
    createdAt = createdAt,
    stock = stock,
    modifierGroups = if (modifierMetadataLoaded) {
        modifierGroups?.map { it.modifierGroupToDomain(tenantId) } ?: emptyList()
    } else {
        emptyList()
    },
    modifierMetadataLoaded = modifierMetadataLoaded,
)

internal fun ProductEntity.toDomain(): Product = Product(
    id = id,
    tenantId = tenantId,
    categoryId = categoryId,
    name = name,
    sku = sku,
    price = price,
    cost = cost,
    minimumStock = minimumStock,
    trackStock = trackStock,
    isActive = isActive,
    createdAt = createdAt,
    stock = null,
    modifierGroups = emptyList(),
    modifierMetadataLoaded = modifierMetadataLoaded,
)

internal suspend fun ProductEntity.toDomain(
    modifierLocalDataSource: ModifierLocalDataSource,
): Product {
    val modifierGroups = if (modifierMetadataLoaded) {
        val assignments = modifierLocalDataSource.getAssignmentsForProducts(tenantId, listOf(id))
        val groupIds = assignments.map { it.modifierGroupId }
        val groups = modifierLocalDataSource.getModifierGroupsForProduct(tenantId, id)
        val options = if (groupIds.isNotEmpty()) {
            modifierLocalDataSource.getOptionsForGroups(tenantId, groupIds)
        } else {
            emptyList()
        }
        groups.map { group ->
            val assignment = assignments.find { it.modifierGroupId == group.id }!!
            group.toDomain(
                required = assignment.required,
                selectionType = assignment.selectionType,
                displayOrder = assignment.displayOrder,
                options = options.filter { it.modifierGroupId == group.id },
            )
        }
    } else {
        emptyList()
    }

    return Product(
        id = id,
        tenantId = tenantId,
        categoryId = categoryId,
        name = name,
        sku = sku,
        price = price,
        cost = cost,
        minimumStock = minimumStock,
        trackStock = trackStock,
        isActive = isActive,
        createdAt = createdAt,
        stock = null,
        modifierGroups = modifierGroups,
        modifierMetadataLoaded = modifierMetadataLoaded,
    )
}
