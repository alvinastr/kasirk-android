package com.kasirkita.pos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kasirkita.pos.core.database.entity.ProductEntity

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE tenantId = :tenantId ORDER BY name ASC")
    suspend fun getProducts(tenantId: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE tenantId = :tenantId")
    suspend fun deleteAll(tenantId: String)

    @Transaction
    suspend fun replaceProducts(tenantId: String, products: List<ProductEntity>) {
        require(products.all { product -> product.tenantId == tenantId }) {
            "Cannot cache products for a different tenant"
        }
        deleteAll(tenantId)
        insertProducts(products)
    }
}
