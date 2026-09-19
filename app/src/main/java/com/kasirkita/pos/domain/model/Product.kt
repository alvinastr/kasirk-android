package com.kasirkita.pos.domain.model

data class Product(
    val id: String,
    val tenantId: String,
    val categoryId: String?,
    val name: String,
    val sku: String,
    val price: Long,
    val cost: Long,
    val minimumStock: Int,
    val trackStock: Boolean,
    val isActive: Boolean,
    val createdAt: String,
)
