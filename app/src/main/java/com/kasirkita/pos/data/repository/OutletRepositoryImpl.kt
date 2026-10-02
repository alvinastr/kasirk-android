package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.OperationalContextDataStore
import com.kasirkita.pos.data.api.OutletApi
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.repository.OutletRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
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
        withContext(NonCancellable) {
            val session = requireNotNull(authSessionDataStore.getSession()) {
                "An authenticated session is required to select an outlet"
            }
            require(outlet.tenantId == session.tenantId) {
                "Outlet does not belong to the current tenant"
            }
            operationalContextDataStore.saveOutlet(session.tenantId, session.userId, outlet)
            _selectedOutlet.value = outlet
        }
    }

    override suspend fun clearSelectedOutlet(tenantId: String?, userId: String?) {
        _selectedOutlet.value = null
        withContext(NonCancellable) {
            val resolvedTenantId = tenantId ?: authSessionDataStore.getSession()?.tenantId
            val resolvedUserId = userId ?: authSessionDataStore.getSession()?.userId
            if (resolvedTenantId != null && resolvedUserId != null) {
                operationalContextDataStore.clearOutlet(resolvedTenantId, resolvedUserId)
            }
        }
    }

    override suspend fun restoreSelectedOutlet() {
        val session = authSessionDataStore.getSession() ?: return
        val restored = try {
            operationalContextDataStore.getOutlet(session.tenantId, session.userId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        if (restored != null && restored.tenantId == session.tenantId) {
            _selectedOutlet.value = restored
        }
    }
}
