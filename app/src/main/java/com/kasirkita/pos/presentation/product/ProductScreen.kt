package com.kasirkita.pos.presentation.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductScreen(
    onCartClick: () -> Unit = {},
    viewModel: ProductCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val currentState = state) {
        ProductCatalogState.Loading -> LoadingContent()
        is ProductCatalogState.Error -> ErrorContent(
            message = currentState.message,
            onRetry = viewModel::loadProducts,
        )
        is ProductCatalogState.Success -> ProductList(
            items = currentState.items,
            message = currentState.message,
            onRefresh = viewModel::refresh,
            onAddToCart = viewModel::addToCart,
            onCartClick = onCartClick,
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Coba lagi")
        }
    }
}

@Composable
private fun ProductList(
    items: List<ProductCatalogItem>,
    message: String?,
    onRefresh: () -> Unit,
    onAddToCart: (ProductCatalogItem) -> Unit,
    onCartClick: () -> Unit,
) {
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh")
        }

        Button(
            onClick = onCartClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Buka Cart")
        }

        message?.let { feedback ->
            Text(
                text = feedback,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Belum ada produk")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = items,
                    key = { item -> item.product.id },
                ) { item ->
                    val product = item.product
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("SKU: ${product.sku}")
                        Text("Harga: Rp${numberFormat.format(product.price)}")
                        Text(
                            if (product.trackStock) {
                                if (item.stockQuantity == 0) {
                                    "Stok: 0 (Stok habis)"
                                } else {
                                    "Stok: ${item.stockQuantity}"
                                }
                            } else {
                                "Stok tidak dikelola"
                            },
                            color = if (product.trackStock && item.stockQuantity == 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Button(
                            onClick = { onAddToCart(item) },
                            enabled = product.isActive && item.canAddToCart,
                        ) {
                            Text("Tambah ke Cart")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
