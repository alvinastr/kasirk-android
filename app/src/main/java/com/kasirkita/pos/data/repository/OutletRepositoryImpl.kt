package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.OutletApi
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.repository.OutletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutletRepositoryImpl @Inject constructor(
    private val outletApi: OutletApi,
) : OutletRepository {

    private val _selectedOutlet = MutableStateFlow<Outlet?>(null)
    override val selectedOutlet: StateFlow<Outlet?> = _selectedOutlet.asStateFlow()

    override suspend fun getOutlets(): Result<List<Outlet>> = runCatching {
        outletApi.getOutlets().map { response -> response.toDomain() }
    }

    override fun selectOutlet(outlet: Outlet) {
        _selectedOutlet.value = outlet
    }
}
