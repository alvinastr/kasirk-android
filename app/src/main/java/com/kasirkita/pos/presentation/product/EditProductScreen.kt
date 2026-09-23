package com.kasirkita.pos.presentation.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Product

@Composable
fun EditProductScreen(
    productId: String,
    onCancel: () -> Unit,
    onProductUpdated: () -> Unit,
    productViewModel: ProductViewModel = hiltViewModel(),
    managementViewModel: ProductManagementViewModel = hiltViewModel(),
) {
    val productState by productViewModel.state.collectAsState()
    val managementState by managementViewModel.state.collectAsState()
    val categoryState by managementViewModel.categoryState.collectAsState()

    LaunchedEffect(managementState) {
        if (managementState is ProductManagementState.Success) {
            onProductUpdated()
        }
    }

    when (val currentState = productState) {
        ProductState.Loading -> EditProductLoading()
        is ProductState.Error -> EditProductLoadError(
            message = "Data produk tidak dapat dimuat.",
            onRetry = productViewModel::refresh,
            onCancel = onCancel,
        )
        is ProductState.Success -> {
            val product = currentState.products.firstOrNull { it.id == productId }
            if (product == null) {
                EditProductLoadError(
                    message = "Produk tidak ditemukan dalam daftar saat ini.",
                    onRetry = productViewModel::refresh,
                    onCancel = onCancel,
                )
            } else {
                EditProductForm(
                    product = product,
                    managementState = managementState,
                    categoryState = categoryState,
                    onInputChanged = managementViewModel::clearError,
                    onRetryCategories = managementViewModel::loadCategories,
                    onSubmit = { changes ->
                        managementViewModel.updateProduct(
                            productId = product.id,
                            name = changes.name,
                            sku = changes.sku,
                            categoryId = changes.categoryId,
                            categoryIdChanged = changes.categoryIdChanged,
                            price = changes.price,
                            cost = changes.cost,
                            minimumStock = changes.minimumStock,
                            trackStock = changes.trackStock,
                        )
                    },
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun EditProductForm(
    product: Product,
    managementState: ProductManagementState,
    categoryState: CategoryState,
    onInputChanged: () -> Unit,
    onRetryCategories: () -> Unit,
    onSubmit: (ProductUpdateChanges) -> Unit,
    onCancel: () -> Unit,
) {
    val initialValues = remember(product) {
        product.toProductFormInitialValues()
    }
    var localError by remember(product.id) { mutableStateOf<String?>(null) }

    ProductForm(
        title = "Edit Produk",
        description = "Perbarui data produk yang perlu diubah.",
        submitLabel = "Simpan Perubahan",
        initialValues = initialValues,
        categoryState = categoryState,
        isLoading = managementState is ProductManagementState.Loading,
        errorMessage = localError ?: (managementState as? ProductManagementState.Error)?.message,
        onInputChanged = {
            localError = null
            onInputChanged()
        },
        onRetryCategories = onRetryCategories,
        onSubmit = { editedProduct ->
            val changes = editedProduct.changesFrom(product)
            if (changes.hasChanges) {
                localError = null
                onSubmit(changes)
            } else {
                localError = "Belum ada perubahan untuk disimpan."
            }
        },
        onCancel = onCancel,
    )
}

@Composable
private fun EditProductLoading() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = "Memuat data produk...",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun EditProductLoadError(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text("Muat Ulang")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text("Kembali")
            }
        }
    }
}

internal data class ProductUpdateChanges(
    val name: String? = null,
    val sku: String? = null,
    val categoryId: String? = null,
    val categoryIdChanged: Boolean = false,
    val price: Long? = null,
    val cost: Long? = null,
    val minimumStock: Int? = null,
    val trackStock: Boolean? = null,
) {
    val hasChanges: Boolean
        get() = name != null ||
            sku != null ||
            categoryIdChanged ||
            price != null ||
            cost != null ||
            minimumStock != null ||
            trackStock != null
}

internal fun ValidatedCreateProduct.changesFrom(
    original: Product,
): ProductUpdateChanges = ProductUpdateChanges(
    name = name.takeIf { it != original.name },
    sku = sku.takeIf { it != original.sku },
    categoryId = categoryId,
    categoryIdChanged = categoryId != original.categoryId,
    price = price.takeIf { it != original.price },
    cost = cost.takeIf { it != original.cost },
    minimumStock = minimumStock.takeIf { it != original.minimumStock },
    trackStock = trackStock.takeIf { it != original.trackStock },
)
