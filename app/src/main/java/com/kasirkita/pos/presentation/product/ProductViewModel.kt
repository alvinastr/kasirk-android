package com.kasirkita.pos.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.usecase.GetProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val getProductsUseCase: GetProductsUseCase,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProductState>(ProductState.Loading)
    val state: StateFlow<ProductState> = _state.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        load { getProductsUseCase() }
    }

    fun refresh() {
        load { productRepository.refreshProducts() }
    }

    private fun load(request: suspend () -> Result<List<Product>>) {
        viewModelScope.launch {
            _state.value = ProductState.Loading
            request().fold(
                onSuccess = { products ->
                    _state.value = ProductState.Success(products)
                },
                onFailure = { throwable ->
                    _state.value = ProductState.Error(
                        throwable.message ?: "Gagal memuat produk.",
                    )
                },
            )
        }
    }
}
