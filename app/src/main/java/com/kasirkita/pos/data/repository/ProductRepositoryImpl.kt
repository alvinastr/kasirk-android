package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.database.entity.ProductEntity
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.local.ProductLocalDataSource
import com.kasirkita.pos.data.model.CreateProductRequest
import com.kasirkita.pos.data.model.ProductResponse
import com.kasirkita.pos.data.model.UpdateProductRequest
import com.kasirkita.pos.data.model.toJsonObject
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.ProductRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productApi: ProductApi,
    private val localDataSource: ProductLocalDataSource,
    private val authSessionDataStore: AuthSessionDataStore,
) : ProductRepository {

    override suspend fun getProducts(): Result<List<Product>> = runCatching {
        val tenantId = requireTenantId()
        val cachedProducts = localDataSource.getProducts(tenantId)
        if (cachedProducts.isNotEmpty()) {
            cachedProducts.map { it.toDomain() }
        } else {
            fetchAndCacheProducts(tenantId)
        }
    }

    override suspend fun refreshProducts(): Result<List<Product>> = runCatching {
        fetchAndCacheProducts(requireTenantId())
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
        ).toEntity().requireTenant(tenantId).toDomain()
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
        ).toEntity().requireTenant(tenantId).toDomain()
    }

    private suspend fun fetchAndCacheProducts(tenantId: String): List<Product> {
        val remoteProducts = productApi.getProducts()
        val entities = remoteProducts.map { response ->
            response.toEntity().requireTenant(tenantId)
        }
        localDataSource.saveProducts(tenantId, entities)
        return entities.map { it.toDomain() }
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

internal fun ProductResponse.toEntity(): ProductEntity = ProductEntity(
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
)
