package com.kasirkita.pos.presentation.outlet

import com.kasirkita.pos.domain.model.Outlet

sealed interface OutletState {
    data object Loading : OutletState
    data class Success(val outlets: List<Outlet>) : OutletState
    data class Error(val message: String) : OutletState
}
