package com.kasirkita.pos.presentation.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    onTransactionClick: (String) -> Unit,
    viewModel: TransactionHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    TransactionHistoryContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::loadTransactions,
        onTransactionClick = onTransactionClick,
    )
}

@Composable
internal fun TransactionHistoryContent(
    state: TransactionHistoryState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTransactionClick: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            TransactionHistoryHeader(onBack = onBack)

            when (val currentState = state) {
                TransactionHistoryState.Loading -> HistoryCenteredContent {
                    CircularProgressIndicator()
                    Text("Memuat riwayat transaksi...")
                }

                TransactionHistoryState.Empty -> HistoryCenteredContent {
                    Text(
                        text = "Belum ada transaksi.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Muat Ulang")
                    }
                }

                is TransactionHistoryState.Error -> HistoryCenteredContent {
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

                is TransactionHistoryState.Success -> TransactionList(
                    transactions = currentState.transactions,
                    onTransactionClick = onTransactionClick,
                )
            }
        }
    }
}

@Composable
private fun TransactionHistoryHeader(onBack: () -> Unit) {
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
            text = "Riwayat Transaksi",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun ColumnScope.HistoryCenteredContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 16.dp,
            alignment = Alignment.CenterVertically,
        ),
        content = content,
    )
}

@Composable
private fun ColumnScope.TransactionList(
    transactions: List<Transaction>,
    onTransactionClick: (String) -> Unit,
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
        modifier = Modifier.weight(1f),
    ) {
        items(
            items = transactions,
            key = Transaction::id,
        ) { transaction ->
            TransactionHistoryRow(
                transaction = transaction,
                formattedDate = formatTransactionDate(
                    value = transaction.createdAt,
                    formatter = dateFormatter,
                ),
                formattedTotal = "Rp${numberFormat.format(transaction.total)}",
                onClick = { onTransactionClick(transaction.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun TransactionHistoryRow(
    transaction: Transaction,
    formattedDate: String,
    formattedTotal: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = formattedTotal,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Pembayaran: " +
                (transaction.payments.firstOrNull()?.method ?: "Lihat detail"),
        )
        Text("Outlet: ${transaction.outletId}")
        TextButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Lihat Transaksi")
        }
    }
}

internal fun formatTransactionDate(
    value: String,
    formatter: DateTimeFormatter,
): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(formatter)
}.getOrDefault(value)
