package com.kasirkita.pos.domain.model

data class HeldOrderPage(
    val items: List<HeldOrder>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int,
)
