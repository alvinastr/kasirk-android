package com.kasirkita.pos.presentation.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.theme.KasirSpacing
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
                .padding(horizontal = KasirSpacing.Large),
        ) {
            TransactionHistoryHeader(onBack = onBack)

            when (val currentState = state) {
                TransactionHistoryState.Loading -> KasirLoadingState(
                    message = "Memuat riwayat transaksi...",
                    modifier = Modifier.weight(1f),
                )

                TransactionHistoryState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        space = KasirSpacing.Large,
                        alignment = Alignment.CenterVertically,
                    ),
                ) {
                    Text(
                        text = "Belum ada transaksi.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    KasirPrimaryButton(
                        text = "Muat Ulang",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is TransactionHistoryState.Error -> KasirErrorState(
                    message = currentState.message,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )

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
            .padding(vertical = KasirSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
    ) {
        KasirTextButton(
            text = "Kembali",
            onClick = onBack,
        )
        Text(
            text = "Riwayat Transaksi",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
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
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
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
            .padding(vertical = KasirSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formattedTotal,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        transaction.payments.firstOrNull()?.method?.let { method ->
            Text(
                text = "Pembayaran: $method",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        KasirPrimaryButton(
            text = "Lihat Transaksi",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(modifier = Modifier.padding(top = KasirSpacing.Small))
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
