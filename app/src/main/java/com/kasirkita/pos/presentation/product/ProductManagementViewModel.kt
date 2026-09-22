package com.kasirkita.pos.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.usecase.CreateProductUseCase
import com.kasirkita.pos.domain.usecase.UpdateProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ProductManagementState>(
        ProductManagementState.Idle,
    )
    val state: StateFlow<ProductManagementState> = _state.asStateFlow()

    fun createProduct(
        name: String,
        sku: String,
        categoryId: String?,
        price: Long,
        cost: Long,
        minimumStock: Int,
        trackStock: Boolean,
    ) {
        execute {
            createProductUseCase(
                name = name,
                sku = sku,
                categoryId = categoryId,
                price = price,
                cost = cost,
                minimumStock = minimumStock,
                trackStock = trackStock,
            )
        }
    }

    fun updateProduct(
        productId: String,
        name: String? = null,
        sku: String? = null,
        categoryId: String? = null,
        price: Long? = null,
        cost: Long? = null,
        minimumStock: Int? = null,
        trackStock: Boolean? = null,
    ) {
        execute {
            updateProductUseCase(
                productId = productId,
                name = name,
                sku = sku,
                categoryId = categoryId,
                price = price,
                cost = cost,
                minimumStock = minimumStock,
                trackStock = trackStock,
            )
        }
    }

    private fun execute(request: suspend () -> Result<Product>) {
        if (_state.value is ProductManagementState.Loading) return

        viewModelScope.launch {
            _state.value = ProductManagementState.Loading
            request().fold(
                onSuccess = { product ->
                    _state.value = ProductManagementState.Success(product)
                },
                onFailure = { throwable ->
                    _state.value = ProductManagementState.Error(
                        throwable.message ?: "Operasi produk gagal.",
                    )
                },
            )
        }
    }
}
