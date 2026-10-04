package com.kasirkita.pos.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "products",
    primaryKeys = ["tenantId", "id"],
)
data class ProductEntity(
    val id: String,
    val tenantId: String,
    val categoryId: String?,
    val name: String,
    val sku: String,
    val price: Long,
    val cost: Long,
    val minimumStock: Int,
    @ColumnInfo(defaultValue = "1")
    val trackStock: Boolean,
    val isActive: Boolean,
    val createdAt: String,
    @ColumnInfo(defaultValue = "0")
    val modifierMetadataLoaded: Boolean = false,
)
