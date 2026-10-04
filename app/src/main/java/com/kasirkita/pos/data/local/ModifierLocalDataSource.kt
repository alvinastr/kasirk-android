package com.kasirkita.pos.data.local

import com.kasirkita.pos.core.database.dao.ModifierGroupDao
import com.kasirkita.pos.core.database.dao.ModifierOptionDao
import com.kasirkita.pos.core.database.dao.ProductModifierGroupDao
import com.kasirkita.pos.core.database.entity.ModifierGroupEntity
import com.kasirkita.pos.core.database.entity.ModifierOptionEntity
import com.kasirkita.pos.core.database.entity.ProductModifierGroupEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModifierLocalDataSource @Inject constructor(
    private val modifierGroupDao: ModifierGroupDao,
    private val modifierOptionDao: ModifierOptionDao,
    private val productModifierGroupDao: ProductModifierGroupDao,
) {
    suspend fun getModifierGroupsForProduct(
        tenantId: String,
        productId: String,
    ): List<ModifierGroupEntity> =
        modifierGroupDao.getModifierGroupsForProduct(tenantId, productId)

    suspend fun getAssignmentsForProducts(
        tenantId: String,
        productIds: List<String>,
    ): List<ProductModifierGroupEntity> =
        productModifierGroupDao.getAssignmentsForProducts(tenantId, productIds)

    suspend fun getOptionsForGroups(
        tenantId: String,
        modifierGroupIds: List<String>,
    ): List<ModifierOptionEntity> =
        modifierOptionDao.getOptionsForGroups(tenantId, modifierGroupIds)

    suspend fun replaceModifierMetadata(
        tenantId: String,
        groups: List<ModifierGroupEntity>,
        options: List<ModifierOptionEntity>,
        assignments: List<ProductModifierGroupEntity>,
    ) {
        require(groups.all { it.tenantId == tenantId }) {
            "Cannot cache modifier groups for a different tenant"
        }
        modifierOptionDao.deleteAllForTenant(tenantId)
        modifierGroupDao.replaceModifierGroups(tenantId, groups)
        productModifierGroupDao.deleteAllForTenant(tenantId)
        modifierOptionDao.insertOptions(options)
        productModifierGroupDao.insertAssignments(assignments)
    }
}
