package com.kasirkita.pos.presentation.home

import com.kasirkita.pos.domain.model.SyncResult

data class HomeSyncState(
    val pendingCount: Int = 0,
    val actionRequiredCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastResult: SyncResult? = null,
    val errorMessage: String? = null,
)
