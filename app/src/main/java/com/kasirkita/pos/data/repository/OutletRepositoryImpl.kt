package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.OperationalContextDataStore
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
    private val authSessionDataStore: AuthSessionDataStore,
    private val operationalContextDataStore: OperationalContextDataStore,
) : OutletRepository {

    private val _selectedOutlet = MutableStateFlow<Outlet?>(null)
    override val selectedOutlet: StateFlow<Outlet?> = _selectedOutlet.asStateFlow()

    override suspend fun getOutlets(): Result<List<Outlet>> = runCatching {
        outletApi.getOutlets().map { response -> response.toDomain() }
    }

    override suspend fun selectOutlet(outlet: Outlet) {
        _selectedOutlet.value = outlet
        val session = authSessionDataStore.getSession()
        if (session != null) {
            operationalContextDataStore.saveOutlet(session.tenantId, session.userId, outlet)
        }
    }

    override suspend fun clearSelectedOutlet() {
        _selectedOutlet.value = null
        val session = authSessionDataStore.getSession()
        if (session != null) {
            operationalContextDataStore.clearOutlet(session.tenantId, session.userId)
        }
    }

    override suspend fun restoreSelectedOutlet() {
        val session = authSessionDataStore.getSession() ?: return
        val restored = operationalContextDataStore.getOutlet(session.tenantId, session.userId)
        if (restored != null && restored.tenantId == session.tenantId) {
            _selectedOutlet.value = restored
        }
    }
}
