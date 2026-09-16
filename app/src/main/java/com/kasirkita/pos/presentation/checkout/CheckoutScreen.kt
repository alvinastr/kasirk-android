package com.kasirkita.pos.presentation.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kasirkita.pos.domain.model.CartItem
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CheckoutScreen(
    onCheckoutSuccess: (String) -> Unit = {},
    viewModel: CheckoutViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }
    var paymentText by rememberSaveable { mutableStateOf("") }
    val transaction = state.transaction

    LaunchedEffect(transaction?.id) {
        transaction?.id?.let(onCheckoutSuccess)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Checkout",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (transaction != null) {
            Text(
                text = "Transaksi berhasil",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Transaction ID: ${transaction.id}")
            Text("Status: ${transaction.status}")
            Text("Total: Rp${numberFormat.format(transaction.total)}")
            transaction.change?.let { change ->
                Text("Kembalian: Rp${numberFormat.format(change)}")
            }
            return@Column
        }

        Text("Outlet: ${state.selectedOutlet?.name ?: "Belum dipilih"}")
        Text("Shift: ${state.currentShift?.status ?: "Tidak aktif"}")

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = state.cart.items,
                key = CartItem::productId,
            ) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${item.name} x${item.quantity}")
                    Text("Rp${numberFormat.format(item.subtotal())}")
                }
            }
        }

        Text(
            text = "Total: Rp${numberFormat.format(state.cart.totalAmount())}",
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Metode pembayaran: CASH")

        OutlinedTextField(
            value = paymentText,
            onValueChange = { value ->
                paymentText = value.filter(Char::isDigit)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Jumlah pembayaran") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                viewModel.checkout(paymentText.toLongOrNull() ?: 0L)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && state.cart.items.isNotEmpty(),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Bayar")
            }
        }
    }
}
