package com.kasirkita.pos.presentation.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.TransactionItem
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.theme.KasirSpacing
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onViewReceipt: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val printState by viewModel.printState.collectAsState()

    TransactionDetailContent(
        state = state,
        printState = printState,
        onBack = onBack,
        onRetry = viewModel::loadTransaction,
        onViewReceipt = onViewReceipt,
        onPrintReceipt = viewModel::printReceipt,
        onDismissPrintError = viewModel::dismissPrintError,
    )
}

@Composable
internal fun TransactionDetailContent(
    state: TransactionDetailState,
    printState: TransactionPrintState = TransactionPrintState.Idle,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onViewReceipt: (String) -> Unit,
    onPrintReceipt: () -> Unit = {},
    onDismissPrintError: () -> Unit = {},
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
                KasirLoadingState(message = "Memuat detail transaksi...")
            }

            is TransactionDetailState.Error -> TransactionDetailMessage(
                onBack = onBack,
            ) {
                KasirErrorState(
                    message = currentState.message,
                    onRetry = onRetry,
                )
            }

            is TransactionDetailState.Success -> TransactionDetailSuccessContent(
                transaction = currentState.transaction,
                printState = printState,
                onBack = onBack,
                onViewReceipt = onViewReceipt,
                onPrintReceipt = onPrintReceipt,
                onDismissPrintError = onDismissPrintError,
            )
        }
    }
}

@Composable
private fun TransactionDetailMessage(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        KasirTextButton(
            text = "Kembali",
            onClick = onBack,
            modifier = Modifier
                .padding(KasirSpacing.Small),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            content()
        }
    }
}

@Composable
private fun TransactionDetailSuccessContent(
    transaction: Transaction,
    printState: TransactionPrintState,
    onBack: () -> Unit,
    onViewReceipt: (String) -> Unit,
    onPrintReceipt: () -> Unit,
    onDismissPrintError: () -> Unit,
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
            .padding(horizontal = KasirSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
    ) {
        item {
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
                    text = "Detail Transaksi",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            KasirCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatTransactionDate(transaction.createdAt, dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "ID: ${transaction.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Status: ${transaction.status}")
                Text(
                    text = "Outlet: ${transaction.outletId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (transaction.status == "completed") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = KasirSpacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
                ) {
                    KasirSecondaryButton(
                        text = "Lihat Struk",
                        onClick = { onViewReceipt(transaction.id) },
                        modifier = Modifier.weight(1f),
                    )
                    KasirPrimaryButton(
                        text = "Cetak Struk",
                        onClick = onPrintReceipt,
                        modifier = Modifier.weight(1f),
                        enabled = !printState.isPrintInFlight(),
                        isLoading = printState.isPrintInFlight(),
                    )
                }
                when (printState) {
                    is TransactionPrintState.Error -> {
                        Text(
                            text = "Gagal cetak: ${printState.message}",
                            color = MaterialTheme.colorScheme.error,
                        )
                        KasirTextButton(
                            text = "Tutup",
                            onClick = onDismissPrintError,
                        )
                    }
                    TransactionPrintState.Success -> {
                        Text(text = "Cetak struk berhasil")
                    }
                    TransactionPrintState.Idle,
                    TransactionPrintState.Loading,
                    -> Unit
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = KasirSpacing.Large))
            Text(
                text = "Item",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = KasirSpacing.Small),
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
            HorizontalDivider(modifier = Modifier.padding(vertical = KasirSpacing.XSmall))
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
            .padding(vertical = KasirSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Text(
            text = "Produk: ${item.productId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Text(
            text = label,
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
        if (emphasized) {
            PriceText(text = "Rp${formatMoney(amount)}")
        } else {
            Text(text = "Rp${formatMoney(amount)}")
        }
    }
}
