package com.kasirkita.pos.data.local

import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.entity.ProductEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductLocalDataSource @Inject constructor(
    private val productDao: ProductDao,
) {
    suspend fun getProducts(): List<ProductEntity> = productDao.getProducts()

    suspend fun saveProducts(products: List<ProductEntity>) {
        productDao.replaceProducts(products)
    }
}
