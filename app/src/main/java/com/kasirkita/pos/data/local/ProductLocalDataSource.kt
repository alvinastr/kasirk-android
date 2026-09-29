package com.kasirkita.pos.data.local

import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.entity.ProductEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductLocalDataSource @Inject constructor(
    private val productDao: ProductDao,
) {
    suspend fun getProducts(tenantId: String): List<ProductEntity> =
        productDao.getProducts(tenantId)

    suspend fun saveProducts(tenantId: String, products: List<ProductEntity>) {
        productDao.replaceProducts(tenantId, products)
    }
}
