package com.kasirkita.pos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kasirkita.pos.core.database.entity.ProductModifierGroupEntity

@Dao
interface ProductModifierGroupDao {

    @Query("""
        SELECT * FROM product_modifier_groups
        WHERE tenantId = :tenantId AND productId = :productId
        ORDER BY displayOrder ASC
    """)
    suspend fun getAssignmentsForProduct(tenantId: String, productId: String): List<ProductModifierGroupEntity>

    @Query("""
        SELECT * FROM product_modifier_groups
        WHERE tenantId = :tenantId AND productId IN (:productIds)
        ORDER BY displayOrder ASC
    """)
    suspend fun getAssignmentsForProducts(tenantId: String, productIds: List<String>): List<ProductModifierGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignments(assignments: List<ProductModifierGroupEntity>)

    @Query("""
        DELETE FROM product_modifier_groups
        WHERE productId IN (SELECT id FROM products WHERE tenantId = :tenantId)
    """)
    suspend fun deleteAllForTenant(tenantId: String)
}
