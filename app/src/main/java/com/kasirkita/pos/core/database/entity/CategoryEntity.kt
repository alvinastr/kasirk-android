package com.kasirkita.pos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import com.kasirkita.pos.domain.model.Category

@Entity(
    tableName = "categories",
    primaryKeys = ["tenantId", "id"],
    indices = [Index(value = ["tenantId"])],
)
data class CategoryEntity(
    val id: String,
    val tenantId: String,
    val name: String,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    tenantId = tenantId,
    name = name,
    createdAt = "",
)
