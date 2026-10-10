package com.kasirkita.pos.presentation.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.sectionTitle

@Composable
fun ReceiptScreen(
    onNewTransaction: () -> Unit = {},
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var isPrinting by remember { mutableStateOf(false) }
    var printError by remember { mutableStateOf<String?>(null) }

    when (val currentState = state) {
        ReceiptState.Loading -> KasirLoadingState(message = "Memuat struk...")

        is ReceiptState.Error -> ReceiptErrorContent(
            message = currentState.message,
            onRetry = viewModel::loadReceipt,
            onNewTransaction = onNewTransaction,
        )

        is ReceiptState.Success -> ReceiptContent(
            receipt = currentState.receipt,
            onNewTransaction = onNewTransaction,
            isPrinting = isPrinting,
            onPrint = {
                viewModel.printReceipt(
                    onPrintStart = { isPrinting = true },
                    onPrintComplete = {
                        isPrinting = false
                        printError = null
                    },
                    onPrintError = { error ->
                        isPrinting = false
                        printError = error
                    },
                )
            },
            printError = printError,
            onPrintErrorDismiss = { printError = null },
        )
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
            .padding(KasirSpacing.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = KasirSpacing.ItemGap,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        KasirCard {
            Text(
                text = message,
                style = MaterialTheme.typography.body,
            )
        }
        KasirSecondaryButton(
            text = "Coba Lagi",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        KasirPrimaryButton(
            text = "Transaksi Baru",
            onClick = onNewTransaction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReceiptContent(
    receipt: Receipt,
    onNewTransaction: () -> Unit,
    isPrinting: Boolean = false,
    onPrint: () -> Unit = {},
    printError: String? = null,
    onPrintErrorDismiss: () -> Unit = {},
) {
    val summary = receipt.toSuccessSummary()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(KasirSpacing.CompactScreenPadding),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            KasirCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Transaksi Berhasil",
                    style = MaterialTheme.typography.sectionTitle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Pembayaran berhasil diproses.",
                    style = MaterialTheme.typography.body,
                )
            }

            KasirCard(modifier = Modifier.fillMaxWidth()) {
                SummaryRow("Total Tagihan", summary.total)
                SummaryRow("Metode Pembayaran", summary.paymentMethod)
                SummaryRow("Jumlah Dibayar", summary.amountPaid)
                SummaryRow("Kembalian", summary.change)
            }
        }

        if (printError != null) {
            KasirCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(KasirSpacing.Large),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(KasirSpacing.Small)) {
                    Text(
                        text = "Gagal cetak: $printError",
                        style = MaterialTheme.typography.body,
                        color = MaterialTheme.colorScheme.error,
                    )
                    KasirSecondaryButton(
                        text = "Tutup",
                        onClick = onPrintErrorDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(KasirSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
        ) {
            KasirPrimaryButton(
                text = if (isPrinting) "Mencetak..." else "Cetak Struk",
                onClick = onPrint,
                enabled = !isPrinting,
                modifier = Modifier.fillMaxWidth(),
            )
            KasirPrimaryButton(
                text = "Transaksi Baru",
                onClick = onNewTransaction,
                enabled = !isPrinting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Long) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.body)
        Text(formatRupiah(amount), style = MaterialTheme.typography.body)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.body)
        Text(value, style = MaterialTheme.typography.body)
    }
}

private fun formatRupiah(amount: Long): String {
    val digits = amount.toString().removePrefix("-").reversed().chunked(3).joinToString(".").reversed()
    return if (amount < 0) "-Rp $digits" else "Rp $digits"
}
