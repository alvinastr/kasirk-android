package com.kasirkita.pos.presentation.receipt

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptItem
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ReceiptScreen(
    viewModel: ReceiptViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val currentState = state) {
        ReceiptState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is ReceiptState.Error -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = currentState.message,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = viewModel::loadReceipt) {
                Text("Coba Lagi")
            }
        }

        is ReceiptState.Success -> ReceiptContent(currentState.receipt)
    }
}

@Composable
private fun ReceiptContent(receipt: Receipt) {
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Receipt",
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
