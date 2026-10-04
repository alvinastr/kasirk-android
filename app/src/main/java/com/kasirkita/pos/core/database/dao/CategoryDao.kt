package com.kasirkita.pos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kasirkita.pos.core.database.entity.CategoryEntity

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE tenantId = :tenantId ORDER BY name ASC")
    suspend fun getCategories(tenantId: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE tenantId = :tenantId")
    suspend fun deleteAll(tenantId: String)

    @Transaction
    suspend fun replaceCategories(tenantId: String, categories: List<CategoryEntity>) {
        require(categories.all { it.tenantId == tenantId }) {
            "Cannot cache categories for a different tenant"
        }
        deleteAll(tenantId)
        insertCategories(categories)
    }
}
