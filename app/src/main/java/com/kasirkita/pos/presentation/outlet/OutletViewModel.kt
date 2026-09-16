package com.kasirkita.pos.presentation.outlet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.usecase.GetOutletsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OutletViewModel @Inject constructor(
    private val getOutletsUseCase: GetOutletsUseCase,
    private val outletRepository: OutletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<OutletState>(OutletState.Loading)
    val state: StateFlow<OutletState> = _state.asStateFlow()
    val selectedOutlet: StateFlow<Outlet?> = outletRepository.selectedOutlet

    init {
        loadOutlets()
    }

    fun loadOutlets() {
        viewModelScope.launch {
            _state.value = OutletState.Loading
            getOutletsUseCase().fold(
                onSuccess = { outlets ->
                    _state.value = OutletState.Success(outlets)
                },
                onFailure = { throwable ->
                    _state.value = OutletState.Error(
                        throwable.message ?: "Gagal memuat outlet.",
                    )
                },
            )
        }
    }

    fun selectOutlet(outlet: Outlet) {
        outletRepository.selectOutlet(outlet)
    }
}
