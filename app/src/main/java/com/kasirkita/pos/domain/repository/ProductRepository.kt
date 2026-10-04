package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Product

interface ProductRepository {
    suspend fun getProducts(
        query: String? = null,
        categoryId: String? = null,
        includeModifiers: Boolean? = null
    ): Result<List<Product>>

    suspend fun refreshProducts(
        query: String? = null,
        categoryId: String? = null,
        includeModifiers: Boolean? = null
    ): Result<List<Product>>

    suspend fun createProduct(
        name: String,
        sku: String,
        categoryId: String?,
        price: Long,
        cost: Long,
        minimumStock: Int,
        trackStock: Boolean,
    ): Result<Product>

    suspend fun updateProduct(
        productId: String,
        name: String? = null,
        sku: String? = null,
        categoryId: String? = null,
        categoryIdChanged: Boolean = false,
        price: Long? = null,
        cost: Long? = null,
        minimumStock: Int? = null,
        trackStock: Boolean? = null,
    ): Result<Product>
}
