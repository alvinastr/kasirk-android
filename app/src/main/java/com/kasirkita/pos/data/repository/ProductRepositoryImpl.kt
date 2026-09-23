package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.database.entity.ProductEntity
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
) : ProductRepository {

    override suspend fun getProducts(): Result<List<Product>> = runCatching {
        val cachedProducts = localDataSource.getProducts()
        if (cachedProducts.isNotEmpty()) {
            cachedProducts.map { it.toDomain() }
        } else {
            fetchAndCacheProducts()
        }
    }

    override suspend fun refreshProducts(): Result<List<Product>> = runCatching {
        fetchAndCacheProducts()
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
        ).toEntity().toDomain()
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
        ).toEntity().toDomain()
    }

    private suspend fun fetchAndCacheProducts(): List<Product> {
        val remoteProducts = productApi.getProducts()
        val entities = remoteProducts.map { it.toEntity() }
        localDataSource.saveProducts(entities)
        return entities.map { it.toDomain() }
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
