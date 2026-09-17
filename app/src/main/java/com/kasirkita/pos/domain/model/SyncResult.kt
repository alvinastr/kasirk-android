package com.kasirkita.pos.domain.model

data class SyncResult(
    val total: Int,
    val synced: Int,
    val failed: Int,
    val pending: Int,
)
