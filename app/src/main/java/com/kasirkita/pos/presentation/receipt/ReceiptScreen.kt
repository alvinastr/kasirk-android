package com.kasirkita.pos.presentation.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptItem
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ReceiptScreen(
    onNewTransaction: () -> Unit = {},
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val currentState = state) {
        ReceiptState.Loading -> ReceiptLoadingContent()

        is ReceiptState.Error -> ReceiptErrorContent(
            message = currentState.message,
            onRetry = viewModel::loadReceipt,
            onNewTransaction = onNewTransaction,
        )

        is ReceiptState.Success -> ReceiptContent(
            receipt = currentState.receipt,
            onNewTransaction = onNewTransaction,
        )
    }
}

@Composable
private fun ReceiptLoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 12.dp,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        Text(
            text = "Transaksi berhasil",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        CircularProgressIndicator()
        Text("Memuat struk...")
    }
}

@Composable
private fun ReceiptErrorContent(
    message: String,
    onRetry: () -> Unit,
    onNewTransaction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 12.dp,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Coba Lagi")
        }
        Button(
            onClick = onNewTransaction,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Transaksi Baru")
        }
    }
}

@Composable
private fun ReceiptContent(
    receipt: Receipt,
    onNewTransaction: () -> Unit,
) {
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Transaksi berhasil",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Struk",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(receipt.tenant.name, style = MaterialTheme.typography.titleMedium)
                Text("Outlet: ${receipt.outlet.name}")
                Text("Kasir: ${receipt.cashier.name}")
                receipt.customer?.let { customer ->
                    Text("Customer: ${customer.name}")
                }
                Text("Transaction ID: ${receipt.transactionId}")
                HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
            }

            items(
                items = receipt.items,
                key = ReceiptItem::id,
            ) { receiptItem ->
                ReceiptItemRow(
                    item = receiptItem,
                    formatMoney = numberFormat::format,
                )
            }

            item {
                HorizontalDivider()
                ReceiptAmountRow("Subtotal", receipt.subtotal, numberFormat::format)
                ReceiptAmountRow("Diskon", receipt.discount, numberFormat::format)
                ReceiptAmountRow("Pajak", receipt.tax, numberFormat::format)
                ReceiptAmountRow("Total", receipt.total, numberFormat::format)

                val payment = receipt.payment
                Text("Payment method: ${payment?.method ?: "-"}")
                Text("Cash received: Rp${numberFormat.format(payment?.amount ?: 0L)}")
                Text("Change: Rp${numberFormat.format(receipt.change ?: 0L)}")
            }
        }

        Button(
            onClick = onNewTransaction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 48.dp),
        ) {
            Text("Transaksi Baru")
        }
    }
}

@Composable
private fun ReceiptItemRow(
    item: ReceiptItem,
    formatMoney: (Long) -> String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(item.productName, style = MaterialTheme.typography.titleSmall)
        Text("SKU: ${item.sku}")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${item.quantity} x Rp${formatMoney(item.unitPrice)}")
            Text("Rp${formatMoney(item.subtotal)}")
        }
    }
}

@Composable
private fun ReceiptAmountRow(
    label: String,
    amount: Long,
    formatMoney: (Long) -> String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text("Rp${formatMoney(amount)}")
    }
}
