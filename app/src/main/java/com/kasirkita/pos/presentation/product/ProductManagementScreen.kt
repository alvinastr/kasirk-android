package com.kasirkita.pos.presentation.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Product
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductManagementScreen(
    onAddProduct: () -> Unit,
    onEditProduct: (Product) -> Unit,
    productCreated: Boolean = false,
    onProductCreatedHandled: () -> Unit = {},
    productUpdated: Boolean = false,
    onProductUpdatedHandled: () -> Unit = {},
    viewModel: ProductViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(productCreated, productUpdated) {
        val successMessage = when {
            productUpdated -> "Perubahan produk berhasil disimpan."
            productCreated -> "Produk berhasil ditambahkan."
            else -> null
        }

        if (successMessage != null) {
            viewModel.refresh()
            snackbarHostState.showSnackbar(successMessage)

            if (productUpdated) {
                onProductUpdatedHandled()
            } else {
                onProductCreatedHandled()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "Kelola Produk",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Periksa harga jual dan pengaturan stok.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Button(
                    onClick = onAddProduct,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Tambah Produk")
                }

                when (val currentState = state) {
                    ProductState.Loading -> ProductManagementLoading(
                        modifier = Modifier.weight(1f),
                    )
                    is ProductState.Error -> ProductManagementError(
                        modifier = Modifier.weight(1f),
                        onRetry = viewModel::loadProducts,
                    )
                    is ProductState.Success -> ProductManagementList(
                        products = currentState.products,
                        onEditProduct = onEditProduct,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ProductManagementLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Memuat daftar produk...",
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ProductManagementError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Daftar produk tidak dapat dimuat.",
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = 16.dp)
                .heightIn(min = 48.dp),
        ) {
            Text("Coba lagi")
        }
    }
}

@Composable
private fun ProductManagementList(
    products: List<Product>,
    onEditProduct: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (products.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Belum ada produk untuk dikelola.")
        }
        return
    }

    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
    ) {
        items(
            items = products,
            key = Product::id,
        ) { product ->
            ProductManagementItem(
                product = product,
                formattedPrice = numberFormat.format(product.price),
                onEdit = { onEditProduct(product) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ProductManagementItem(
    product: Product,
    formattedPrice: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Rp$formattedPrice",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "SKU: ${product.sku}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (product.trackStock) {
                    "Stok: Dikelola"
                } else {
                    "Stok: Tidak dikelola"
                },
            )
        }

        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Edit")
        }
    }
}
