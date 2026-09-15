package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Product

interface ProductRepository {
    suspend fun getProducts(): Result<List<Product>>

    suspend fun refreshProducts(): Result<List<Product>>
}
