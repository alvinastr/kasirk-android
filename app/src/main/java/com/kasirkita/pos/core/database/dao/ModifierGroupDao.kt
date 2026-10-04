package com.kasirkita.pos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kasirkita.pos.core.database.entity.ModifierGroupEntity

@Dao
interface ModifierGroupDao {

    @Query("SELECT * FROM modifier_groups WHERE tenantId = :tenantId")
    suspend fun getModifierGroups(tenantId: String): List<ModifierGroupEntity>

    @Query("""
        SELECT mg.* FROM modifier_groups mg
        INNER JOIN product_modifier_groups pmg
            ON mg.id = pmg.modifierGroupId AND mg.tenantId = pmg.tenantId
        WHERE pmg.productId = :productId AND mg.tenantId = :tenantId
        ORDER BY pmg.displayOrder ASC
    """)
    suspend fun getModifierGroupsForProduct(
        tenantId: String,
        productId: String,
    ): List<ModifierGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModifierGroups(groups: List<ModifierGroupEntity>)

    @Query("DELETE FROM modifier_groups WHERE tenantId = :tenantId")
    suspend fun deleteAll(tenantId: String)

    @Transaction
    suspend fun replaceModifierGroups(tenantId: String, groups: List<ModifierGroupEntity>) {
        require(groups.all { it.tenantId == tenantId }) {
            "Cannot cache modifier groups for a different tenant"
        }
        deleteAll(tenantId)
        insertModifierGroups(groups)
    }
}
