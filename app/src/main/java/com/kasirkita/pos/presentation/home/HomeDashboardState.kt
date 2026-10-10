package com.kasirkita.pos.presentation.home

import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.presentation.cart.HeldOrderTotalState

data class HomeContextKey(
    val tenantId: String?,
    val userId: String?,
    val role: UserRole?,
    val outletId: String?,
    val shiftId: String?,
    val cashierSessionId: String?,
)

sealed interface DashboardMetric<out T> {
    data object NotRequested : DashboardMetric<Nothing>
    data object Loading : DashboardMetric<Nothing>
    data class Success<T>(val value: T) : DashboardMetric<T>
    data class Error(val message: String) : DashboardMetric<Nothing>
}

data class HomeDashboardState(
    val dailySales: DashboardMetric<DailySalesReport> = DashboardMetric.NotRequested,
    val shiftSummary: DashboardMetric<ShiftSummary> = DashboardMetric.NotRequested,
    val contextKey: HomeContextKey? = null,
)

sealed interface HeldOrderHomePresentation {
    data object NOT_LOADED : HeldOrderHomePresentation
    data object LOADING : HeldOrderHomePresentation
    data class AVAILABLE(val total: Int) : HeldOrderHomePresentation
    data class STALE(val total: Int) : HeldOrderHomePresentation
    data class ERROR(val reason: String) : HeldOrderHomePresentation
}

internal fun heldOrderHomePresentation(state: HeldOrderTotalState): HeldOrderHomePresentation = when (state) {
    HeldOrderTotalState.NotLoaded -> HeldOrderHomePresentation.NOT_LOADED
    is HeldOrderTotalState.Loading -> HeldOrderHomePresentation.LOADING
    is HeldOrderTotalState.Available -> HeldOrderHomePresentation.AVAILABLE(state.total)
    is HeldOrderTotalState.Stale -> HeldOrderHomePresentation.STALE(state.previousValue)
    is HeldOrderTotalState.Error -> HeldOrderHomePresentation.ERROR(state.reason)
}
