package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.Outlet
import kotlinx.coroutines.flow.StateFlow

interface OutletRepository {
    val selectedOutlet: StateFlow<Outlet?>

    suspend fun getOutlets(): Result<List<Outlet>>

    fun selectOutlet(outlet: Outlet)
}
