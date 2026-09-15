package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.database.entity.ProductEntity
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.local.ProductLocalDataSource
import com.kasirkita.pos.data.model.ProductResponse
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

    private suspend fun fetchAndCacheProducts(): List<Product> {
        val remoteProducts = productApi.getProducts()
        val entities = remoteProducts.map { it.toEntity() }
        localDataSource.saveProducts(entities)
        return entities.map { it.toDomain() }
    }

    private fun ProductResponse.toEntity(): ProductEntity = ProductEntity(
        id = id,
        tenantId = tenantId,
        categoryId = categoryId,
        name = name,
        sku = sku,
        price = price,
        cost = cost,
        minimumStock = minimumStock,
        isActive = isActive,
        createdAt = createdAt,
    )

    private fun ProductEntity.toDomain(): Product = Product(
        id = id,
        tenantId = tenantId,
        categoryId = categoryId,
        name = name,
        sku = sku,
        price = price,
        cost = cost,
        minimumStock = minimumStock,
        isActive = isActive,
        createdAt = createdAt,
    )
}
