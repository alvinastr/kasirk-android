package com.kasirkita.pos.presentation.product

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CreateProductScreen(
    onCancel: () -> Unit,
    onProductCreated: () -> Unit,
    viewModel: ProductManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is ProductManagementState.Success) {
            onProductCreated()
        }
    }

    ProductForm(
        title = "Tambah Produk",
        description = "Isi data utama produk, lalu simpan.",
        submitLabel = "Simpan Produk",
        initialValues = ProductFormInitialValues(),
        isLoading = state is ProductManagementState.Loading,
        errorMessage = (state as? ProductManagementState.Error)?.message,
        onInputChanged = viewModel::clearError,
        onSubmit = { product ->
            viewModel.createProduct(
                name = product.name,
                sku = product.sku,
                categoryId = product.categoryId,
                price = product.price,
                cost = product.cost,
                minimumStock = product.minimumStock,
                trackStock = product.trackStock,
            )
        },
        onCancel = onCancel,
    )
}
