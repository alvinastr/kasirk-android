package com.kasirkita.pos.presentation.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kasirkita.pos.domain.model.CartItem
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CartScreen(
    viewModel: CartViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cart = state.cart
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Cart (${cart.totalItems()} item)",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (cart.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cart masih kosong")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(
                    items = cart.items,
                    key = CartItem::productId,
                ) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("SKU: ${item.sku}")
                        Text("Harga: Rp${numberFormat.format(item.price)}")
                        Text("Subtotal: Rp${numberFormat.format(item.subtotal())}")

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { viewModel.decreaseQuantity(item.productId) },
                            ) {
                                Text("−")
                            }
                            Text("${item.quantity}")
                            TextButton(
                                onClick = { viewModel.increaseQuantity(item.productId) },
                            ) {
                                Text("+")
                            }
                            TextButton(
                                onClick = { viewModel.removeProduct(item.productId) },
                            ) {
                                Text("Hapus")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Text(
            text = "Total: Rp${numberFormat.format(cart.totalAmount())}",
            modifier = Modifier.padding(vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge,
        )

        Button(
            onClick = { /* Checkout will be implemented in the transaction module. */ },
            modifier = Modifier.fillMaxWidth(),
            enabled = cart.items.isNotEmpty(),
        ) {
            Text("Checkout")
        }
    }
}
