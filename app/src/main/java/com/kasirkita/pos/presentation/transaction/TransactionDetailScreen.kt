package com.kasirkita.pos.presentation.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.TransactionItem
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    TransactionDetailContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::loadTransaction,
    )
}

@Composable
internal fun TransactionDetailContent(
    state: TransactionDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        when (val currentState = state) {
            TransactionDetailState.Loading -> TransactionDetailMessage(
                onBack = onBack,
            ) {
                CircularProgressIndicator()
                Text("Memuat detail transaksi...")
            }

            is TransactionDetailState.Error -> TransactionDetailMessage(
                onBack = onBack,
            ) {
                Text(
                    text = currentState.message,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Coba Lagi")
                }
            }

            is TransactionDetailState.Success -> TransactionDetailSuccessContent(
                transaction = currentState.transaction,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun TransactionDetailMessage(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .heightIn(min = 48.dp),
        ) {
            Text("Kembali")
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TransactionDetailSuccessContent(
    transaction: Transaction,
    onBack: () -> Unit,
) {
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern(
            "dd MMM yyyy, HH.mm",
            Locale.forLanguageTag("id-ID"),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Kembali")
                }
                Text(
                    text = "Detail Transaksi",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                text = formatTransactionDate(transaction.createdAt, dateFormatter),
                style = MaterialTheme.typography.titleMedium,
            )
            Text("ID: ${transaction.id}")
            Text("Status: ${transaction.status}")
            Text("Outlet: ${transaction.outletId}")
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Item",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(
            items = transaction.items,
            key = TransactionItem::id,
        ) { item ->
            TransactionItemRow(
                item = item,
                formatMoney = numberFormat::format,
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            val payment = transaction.payments.firstOrNull()
            Text(
                text = "Pembayaran",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Metode: ${payment?.method ?: "Tidak tersedia"}")
            payment?.let {
                Text("Dibayar: Rp${numberFormat.format(it.amount)}")
            }
            AmountRow("Subtotal", transaction.subtotal, numberFormat::format)
            AmountRow("Diskon", transaction.discount, numberFormat::format)
            AmountRow("Pajak", transaction.tax, numberFormat::format)
            AmountRow(
                label = "Total",
                amount = transaction.total,
                formatMoney = numberFormat::format,
                emphasized = true,
            )
            AmountRow(
                label = "Kembalian",
                amount = transaction.change ?: 0L,
                formatMoney = numberFormat::format,
            )
        }
    }
}

@Composable
private fun TransactionItemRow(
    item: TransactionItem,
    formatMoney: (Long) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Produk: ${item.productId}",
            style = MaterialTheme.typography.titleSmall,
        )
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
private fun AmountRow(
    label: String,
    amount: Long,
    formatMoney: (Long) -> String,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
        Text(
            text = "Rp${formatMoney(amount)}",
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
    }
}
