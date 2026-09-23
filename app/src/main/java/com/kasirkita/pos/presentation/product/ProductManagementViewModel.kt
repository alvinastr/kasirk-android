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
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
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

    fun clearError() {
        if (_state.value is ProductManagementState.Error) {
            _state.value = ProductManagementState.Idle
        }
    }

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
        categoryIdChanged: Boolean = false,
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
                categoryIdChanged = categoryIdChanged,
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
                        productManagementErrorMessage(throwable),
                    )
                },
            )
        }
    }
}

internal fun productManagementErrorMessage(throwable: Throwable): String {
    if (throwable is HttpException) {
        val errorBody = runCatching {
            throwable.response()?.errorBody()?.string()
        }.getOrNull().orEmpty()
        val normalizedBody = errorBody.uppercase(Locale.ROOT)

        return when {
            "SKU_ALREADY_EXISTS" in normalizedBody ->
                "SKU sudah digunakan. Gunakan SKU lain."
            "CATEGORY_NOT_FOUND" in normalizedBody ->
                "Kategori tidak ditemukan. Pilih kategori lain atau gunakan Tanpa kategori."
            "PRODUCT_NOT_FOUND" in normalizedBody ->
                "Produk tidak ditemukan. Daftar produk mungkin sudah berubah."
            "VALIDATION_ERROR" in normalizedBody ||
                throwable.code() == 400 ||
                throwable.code() == 422 ->
                "Data produk belum valid. Periksa kembali data yang wajib diisi."
            throwable.code() == 401 ->
                "Sesi login berakhir. Silakan login kembali."
            throwable.code() == 403 ->
                "Anda tidak memiliki izin untuk mengelola produk."
            else -> "Produk tidak dapat disimpan. Coba lagi."
        }
    }

    return if (throwable is IOException) {
        "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    } else {
        "Produk tidak dapat disimpan. Coba lagi."
    }
}
