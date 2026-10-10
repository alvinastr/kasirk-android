package com.kasirkita.pos.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.GetDailySalesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.kasirkita.pos.presentation.offline.offlineRecoveryErrorMessage

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDailySales: GetDailySalesUseCase,
    private val shiftRepository: ShiftRepository,
    offlineSyncRepository: com.kasirkita.pos.domain.repository.OfflineSyncRepository,
    private val syncPendingTransactions: com.kasirkita.pos.domain.usecase.SyncPendingTransactionsUseCase,
) : ViewModel() {
    private val _syncState = MutableStateFlow(HomeSyncState())
    val syncState: StateFlow<HomeSyncState> = _syncState.asStateFlow()

    private val _dashboardState = MutableStateFlow(HomeDashboardState())
    val dashboardState: StateFlow<HomeDashboardState> = _dashboardState.asStateFlow()
    private var dailyJob: Job? = null
    private var shiftJob: Job? = null
    private var dailyRequestToken = 0L
    private var shiftRequestToken = 0L
    private var activeContext: HomeContextKey? = null

    init {
        viewModelScope.launch {
            offlineSyncRepository.observeQueueSummary().collect { summary ->
                _syncState.update { state ->
                    state.copy(pendingCount = summary.pendingCount, actionRequiredCount = summary.failedCount)
                }
            }
        }
    }

    fun refresh(
        role: UserRole?,
        outletId: String?,
        shiftId: String?,
        tenantId: String? = null,
        userId: String? = null,
        cashierSessionId: String? = shiftId,
        force: Boolean = false,
    ) {
        val context = HomeContextKey(tenantId, userId, role, outletId, shiftId, cashierSessionId)
        if (!force && activeContext == context && (dailyJob?.isActive == true || shiftJob?.isActive == true)) return
        dailyJob?.cancel()
        shiftJob?.cancel()
        dailyRequestToken += 1
        shiftRequestToken += 1
        activeContext = context
        _dashboardState.value = HomeDashboardState(
            dailySales = if (homeLoadsDailySales(role)) DashboardMetric.Loading else DashboardMetric.NotRequested,
            shiftSummary = if (shiftId != null && outletId != null) DashboardMetric.Loading else DashboardMetric.NotRequested,
            contextKey = context,
        )
        if (homeLoadsDailySales(role)) {
            val token = ++dailyRequestToken
            dailyJob = viewModelScope.launch { loadDaily(context, token) }
        }
        if (shiftId != null && outletId != null) {
            val token = ++shiftRequestToken
            shiftJob = viewModelScope.launch { loadShiftSummary(context, token) }
        }
    }

    fun retryDaily(role: UserRole?, outletId: String?) {
        val context = activeContext ?: return
        if (!homeLoadsDailySales(role) || context.role != role || context.outletId != outletId) return
        if (dailyJob?.isActive == true) return
        val token = ++dailyRequestToken
        _dashboardState.update { it.copy(dailySales = DashboardMetric.Loading) }
        dailyJob = viewModelScope.launch { loadDaily(context, token) }
    }

    fun retryShift(outletId: String?, shiftId: String?) {
        val context = activeContext ?: return
        if (outletId == null || shiftId == null || context.outletId != outletId || context.shiftId != shiftId) return
        if (shiftJob?.isActive == true) return
        val token = ++shiftRequestToken
        _dashboardState.update { it.copy(shiftSummary = DashboardMetric.Loading) }
        shiftJob = viewModelScope.launch { loadShiftSummary(context, token) }
    }

    private suspend fun loadDaily(context: HomeContextKey, token: Long) {
        if (!homeLoadsDailySales(context.role)) return
        val result = getDailySales(LocalDate.now().toString(), context.outletId)
        if (token != dailyRequestToken || activeContext != context) return
        _dashboardState.update { state ->
            state.copy(dailySales = result.fold(
                onSuccess = { report ->
                    if (context.outletId != null && report.outletId != context.outletId) {
                        DashboardMetric.Error("Data penjualan tidak sesuai dengan outlet aktif.")
                    } else {
                        DashboardMetric.Success(report)
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    DashboardMetric.Error(homeMetricError(error))
                },
            ))
        }
    }

    private suspend fun loadShiftSummary(context: HomeContextKey, token: Long) {
        val outletId = context.outletId ?: return
        val shiftId = context.shiftId ?: return
        val result = shiftRepository.getShiftSummary(shiftId)
        if (token != shiftRequestToken || activeContext != context) return
        _dashboardState.update { state ->
            state.copy(shiftSummary = result.fold(
                onSuccess = { summary ->
                    if (summary.shiftId != shiftId || summary.outlet.id != outletId) {
                        DashboardMetric.Error("Data shift tidak sesuai dengan konteks aktif.")
                    } else {
                        DashboardMetric.Success(summary)
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    DashboardMetric.Error(homeMetricError(error))
                },
            ))
        }
    }

    fun syncNow() {
        if (_syncState.value.isSyncing) return
        viewModelScope.launch {
            _syncState.update { it.copy(isSyncing = true, lastResult = null, errorMessage = null) }
            syncPendingTransactions().fold(
                onSuccess = { outcome ->
                    _syncState.update {
                        when (outcome) {
                            is com.kasirkita.pos.domain.model.SyncOutcome.Completed -> it.copy(isSyncing = false, lastResult = outcome.result)
                            is com.kasirkita.pos.domain.model.SyncOutcome.RetryableFailure -> it.copy(isSyncing = false, lastResult = outcome.result, errorMessage = offlineRecoveryErrorMessage(outcome.message))
                            com.kasirkita.pos.domain.model.SyncOutcome.AuthenticationUnavailable -> it.copy(isSyncing = false, errorMessage = "Sesi autentikasi tidak tersedia")
                            is com.kasirkita.pos.domain.model.SyncOutcome.ActionRequired -> it.copy(isSyncing = false, lastResult = outcome.result, errorMessage = "Ada transaksi yang memerlukan tindakan")
                        }
                    }
                },
                onFailure = { error -> _syncState.update { it.copy(isSyncing = false, errorMessage = offlineRecoveryErrorMessage(error)) } },
            )
        }
    }

}

internal fun homeMetricError(error: Throwable): String = when {
    error is IOException -> "Tidak dapat terhubung ke server. Coba lagi."
    else -> "Data operasional tidak dapat dimuat. Coba lagi."
}

internal fun homeLoadsDailySales(role: UserRole?): Boolean = role == UserRole.OWNER || role == UserRole.ADMIN
