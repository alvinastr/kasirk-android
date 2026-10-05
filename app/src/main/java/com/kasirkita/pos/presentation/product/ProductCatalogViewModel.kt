package com.kasirkita.pos.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.repository.CartUpdateResult
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.usecase.AddToCartUseCase
import com.kasirkita.pos.domain.usecase.GetCategoriesUseCase
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
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getStocksUseCase: GetStocksUseCase,
    private val productRepository: ProductRepository,
    private val outletRepository: OutletRepository,
    private val addToCartUseCase: AddToCartUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ProductCatalogState>(ProductCatalogState.Loading)
    val state: StateFlow<ProductCatalogState> = _state.asStateFlow()
    private var products: List<Product> = emptyList()
    private var categories: List<Category> = emptyList()
    private var stocks: List<Stock>? = null
    private var hasLoadedProducts = false

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _state.value = ProductCatalogState.Loading
            coroutineScope {
                val productsResult = async { getProductsUseCase() }
                val categoriesResult = async { getCategoriesUseCase() }
                productsResult.await().fold(
                    onSuccess = { cachedProducts ->
                        products = cachedProducts
                        stocks = null
                        hasLoadedProducts = true
                        categories = categoriesResult.await().getOrDefault(emptyList())
                        publishCatalog()
                        refreshRemoteCatalog()
                    },
                    onFailure = {
                        categoriesResult.await()
                        _state.value = ProductCatalogState.Error(
                            "Produk tidak dapat dimuat. Coba lagi.",
                        )
                    },
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!hasLoadedProducts) _state.value = ProductCatalogState.Loading
            refreshRemoteCatalog()
        }
    }

    fun addToCart(item: ProductCatalogItem) {
        val currentState = _state.value as? ProductCatalogState.Success ?: return
        val message = when (productTapAction(item.product)) {
            ProductTapAction.DirectAdd -> when (
                addToCartUseCase(product = item.product, availableStock = item.stockQuantity)
            ) {
                CartUpdateResult.UPDATED -> null
                CartUpdateResult.STOCK_LIMIT_REACHED -> "Jumlah di cart sudah mencapai stok yang tersedia."
            }
            ProductTapAction.BlockedUnknownModifiers ->
                "Pilihan produk belum tersedia. Muat ulang katalog sebelum menambahkan produk ini."
            ProductTapAction.RequiresModifierSelection ->
                "Produk ini memerlukan pilihan opsi sebelum dapat ditambahkan."
            ProductTapAction.BlockedUnavailable -> "Produk tidak tersedia untuk dijual."
        }
        _state.value = currentState.copy(message = message)
    }

    private suspend fun refreshRemoteCatalog() = coroutineScope {
        val productRefresh = async { productRepository.refreshProducts() }
        val categoryRefresh = async { getCategoriesUseCase() }
        val stockRefresh = outletRepository.selectedOutlet.value?.let { outlet ->
            async { getStocksUseCase(outlet.id) }
        }
        val productResult = productRefresh.await()
        val stockResult = stockRefresh?.await()
        categoryRefresh.await().onSuccess { categories = it }

        productResult.onSuccess {
            products = it
            hasLoadedProducts = true
        }
        stocks = stockResult?.getOrNull()

        if (productResult.isFailure && !hasLoadedProducts) {
            _state.value = ProductCatalogState.Error("Produk tidak dapat dimuat. Coba lagi.")
        } else {
            publishCatalog()
        }
    }

    private fun publishCatalog() {
        _state.value = ProductCatalogState.Success(
            items = mapProductsWithStock(products, stocks),
            categories = categories,
        )
    }
}
