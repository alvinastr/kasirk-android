package com.kasirkita.pos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kasirkita.pos.core.database.entity.ModifierOptionEntity

@Dao
interface ModifierOptionDao {

    @Query("""
        SELECT * FROM modifier_options
        WHERE tenantId = :tenantId AND modifierGroupId = :modifierGroupId
        ORDER BY displayOrder ASC
    """)
    suspend fun getOptionsForGroup(tenantId: String, modifierGroupId: String): List<ModifierOptionEntity>

    @Query("""
        SELECT * FROM modifier_options
        WHERE tenantId = :tenantId AND modifierGroupId IN (:modifierGroupIds)
        ORDER BY displayOrder ASC
    """)
    suspend fun getOptionsForGroups(tenantId: String, modifierGroupIds: List<String>): List<ModifierOptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOptions(options: List<ModifierOptionEntity>)

    @Query("DELETE FROM modifier_options WHERE modifierGroupId IN (SELECT id FROM modifier_groups WHERE tenantId = :tenantId)")
    suspend fun deleteAllForTenant(tenantId: String)
}
