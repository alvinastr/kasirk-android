package com.kasirkita.pos.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.CartUpdateResult
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.GetProductsUseCase
import com.kasirkita.pos.domain.usecase.GetStocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductCatalogViewModel @Inject constructor(
    private val getProductsUseCase: GetProductsUseCase,
    private val getStocksUseCase: GetStocksUseCase,
    private val productRepository: ProductRepository,
    private val outletRepository: OutletRepository,
    private val addToCartUseCase: AddToCartUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ProductCatalogState>(ProductCatalogState.Loading)
    val state: StateFlow<ProductCatalogState> = _state.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        load { getProductsUseCase() }
    }

    fun refresh() {
        load { productRepository.refreshProducts() }
    }

    fun addToCart(item: ProductCatalogItem) {
        val result = addToCartUseCase(
            product = item.product,
            availableStock = item.stockQuantity,
        )
        val currentState = _state.value as? ProductCatalogState.Success ?: return
        _state.value = currentState.copy(
            message = when (result) {
                CartUpdateResult.UPDATED -> null
                CartUpdateResult.STOCK_LIMIT_REACHED ->
                    "Jumlah di cart sudah mencapai stok yang tersedia."
            },
        )
    }

    private fun load(productRequest: suspend () -> Result<List<Product>>) {
        viewModelScope.launch {
            _state.value = ProductCatalogState.Loading
            val outlet = outletRepository.selectedOutlet.value
            if (outlet == null) {
                _state.value = ProductCatalogState.Error(
                    "Outlet belum dipilih. Pilih outlet lalu coba lagi.",
                )
                return@launch
            }

            val catalogResult = coroutineScope {
                val products = async { productRequest() }
                val stocks = async { getStocksUseCase(outlet.id) }

                runCatching {
                    mapProductsWithStock(
                        products = products.await().getOrThrow(),
                        stocks = stocks.await().getOrThrow(),
                    )
                }
            }

            catalogResult.fold(
                onSuccess = { items ->
                    _state.value = ProductCatalogState.Success(items)
                },
                onFailure = {
                    _state.value = ProductCatalogState.Error(
                        "Produk dan stok tidak dapat dimuat. Coba lagi.",
                    )
                },
            )
        }
    }
}
