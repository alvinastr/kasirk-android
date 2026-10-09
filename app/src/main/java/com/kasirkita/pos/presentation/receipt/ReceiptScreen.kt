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
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.sectionTitle
import com.kasirkita.pos.ui.theme.supporting

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
            document = currentState.document,
            usedLegacyFallback = currentState.usedLegacyFallback,
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
    document: ReceiptDocument,
    usedLegacyFallback: Boolean,
    onNewTransaction: () -> Unit,
    isPrinting: Boolean = false,
    onPrint: () -> Unit = {},
    printError: String? = null,
    onPrintErrorDismiss: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    text = "Preview struk ${document.paperWidthMm} mm (${document.characterWidth} karakter)",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (usedLegacyFallback) {
                    Text(
                        text = "Template tidak tersedia. Format bawaan digunakan.",
                        style = MaterialTheme.typography.supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            KasirCard(modifier = Modifier.fillMaxWidth()) {
                ReceiptDocumentPreview(document = document)
            }
        }

        if (printError != null) {
            KasirCard(
                modifier = Modifier
                    .fillMaxWidth()
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
