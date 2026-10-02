package com.kasirkita.pos.presentation.offline

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.OfflineTransaction
import com.kasirkita.pos.domain.model.OfflineTransactionFailureType
import com.kasirkita.pos.ui.components.StatusBadge
import com.kasirkita.pos.ui.components.StatusBadgeTone
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.theme.KasirSpacing
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OfflineRecoveryScreen(
    onBack: () -> Unit,
    viewModel: OfflineRecoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    OfflineRecoveryContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::loadTransactions,
        onRetry = viewModel::retry,
        onDelete = viewModel::delete,
        onAcknowledge = viewModel::acknowledge,
    )
}

@Composable
internal fun OfflineRecoveryContent(
    state: OfflineRecoveryState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAcknowledge: (String) -> Unit,
) {
    var confirmation by remember { mutableStateOf<RecoveryConfirmation?>(null) }

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
            OfflineRecoveryHeader(onBack)

            state.noticeMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            state.errorMessage?.takeIf { state.transactions.isNotEmpty() }?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            when {
                state.isLoading && state.transactions.isEmpty() -> RecoveryCenteredContent {
                    KasirLoadingState(message = "Memuat transaksi offline...")
                }
                state.errorMessage != null && state.transactions.isEmpty() ->
                    RecoveryCenteredContent {
                        KasirErrorState(
                            message = state.errorMessage,
                            onRetry = onRefresh,
                        )
                    }
                state.transactions.isEmpty() -> RecoveryCenteredContent {
                    KasirEmptyState(
                        message = "Tidak ada transaksi yang perlu dipulihkan.\nTransaksi offline yang bermasalah akan muncul di sini.",
                    )
                }
                else -> OfflineRecoveryList(
                    transactions = state.transactions,
                    processingClientTransactionId = state.processingClientTransactionId,
                    onRetry = onRetry,
                    onRequestDelete = { transaction ->
                        confirmation = RecoveryConfirmation.Delete(transaction)
                    },
                    onRequestAcknowledge = { transaction ->
                        confirmation = RecoveryConfirmation.Acknowledge(transaction)
                    },
                )
            }
        }
    }

    confirmation?.let { pending ->
        RecoveryConfirmationDialog(
            confirmation = pending,
            onDismiss = { confirmation = null },
            onConfirm = {
                when (pending) {
                    is RecoveryConfirmation.Delete ->
                        onDelete(pending.transaction.clientTransactionId)
                    is RecoveryConfirmation.Acknowledge ->
                        onAcknowledge(pending.transaction.clientTransactionId)
                }
                confirmation = null
            },
        )
    }
}

@Composable
private fun OfflineRecoveryHeader(onBack: () -> Unit) {
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
            text = "Pemulihan Transaksi Offline",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColumnScope.RecoveryCenteredContent(
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
private fun ColumnScope.OfflineRecoveryList(
    transactions: List<OfflineTransaction>,
    processingClientTransactionId: String?,
    onRetry: (String) -> Unit,
    onRequestDelete: (OfflineTransaction) -> Unit,
    onRequestAcknowledge: (OfflineTransaction) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        items(
            items = transactions,
            key = { transaction -> transaction.clientTransactionId },
        ) { transaction ->
            OfflineRecoveryRow(
                transaction = transaction,
                formattedDate = formatOfflineTransactionDate(
                    transaction.createdAt,
                    dateFormatter,
                ),
                formattedTotal = transaction.summary?.total?.let { total ->
                    "Rp${numberFormat.format(total)}"
                },
                formattedPayment = transaction.summary?.paymentAmount?.let { amount ->
                    "Rp${numberFormat.format(amount)}"
                },
                isProcessing = processingClientTransactionId ==
                    transaction.clientTransactionId,
                actionsEnabled = processingClientTransactionId == null,
                onRetry = { onRetry(transaction.clientTransactionId) },
                onRequestDelete = { onRequestDelete(transaction) },
                onRequestAcknowledge = { onRequestAcknowledge(transaction) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun OfflineRecoveryRow(
    transaction: OfflineTransaction,
    formattedDate: String,
    formattedTotal: String?,
    formattedPayment: String?,
    isProcessing: Boolean,
    actionsEnabled: Boolean,
    onRetry: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestAcknowledge: () -> Unit,
) {
    val actions = recoveryActionsFor(transaction.failureType)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
    ) {
        StatusBadge(
            text = offlineRecoveryStatusLabel(transaction.failureType),
            tone = when (transaction.failureType) {
                OfflineTransactionFailureType.RETRYABLE -> StatusBadgeTone.Neutral
                OfflineTransactionFailureType.REJECTED -> StatusBadgeTone.Error
                OfflineTransactionFailureType.RECONCILIATION_REQUIRED -> StatusBadgeTone.Warning
                null -> StatusBadgeTone.Error
            },
        )
        Text(formattedDate)
        formattedTotal?.let { total ->
            Text(
                text = total,
                style = MaterialTheme.typography.headlineSmall,
            )
        } ?: Text("Rincian nilai transaksi tidak tersedia.")
        transaction.summary?.let { summary ->
            Text("${summary.itemCount} item, pembayaran ${formattedPayment.orEmpty()}")
        }
        Text(offlineRecoveryDescription(transaction))

        if (transaction.failureType == OfflineTransactionFailureType.RECONCILIATION_REQUIRED) {
            Text("ID transaksi server")
            SelectionContainer {
                Text(transaction.serverTransactionId ?: "Tidak tersedia")
            }
        }

        if (OfflineRecoveryAction.RETRY in actions) {
            KasirPrimaryButton(
                text = "Coba Sinkronkan",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
                isLoading = isProcessing,
            )
        }
        if (OfflineRecoveryAction.DELETE in actions) {
            KasirSecondaryButton(
                text = "Hapus dari Perangkat",
                onClick = onRequestDelete,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
            )
        }
        if (OfflineRecoveryAction.ACKNOWLEDGE in actions) {
            KasirPrimaryButton(
                text = "Saya Sudah Memeriksa",
                onClick = onRequestAcknowledge,
                modifier = Modifier.fillMaxWidth(),
                enabled = actionsEnabled,
                isLoading = isProcessing,
            )
        }
    }
}

@Composable
private fun RecoveryConfirmationDialog(
    confirmation: RecoveryConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val deleting = confirmation is RecoveryConfirmation.Delete
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (deleting) "Hapus transaksi offline?" else "Konfirmasi pemeriksaan?")
        },
        text = {
            Text(
                if (deleting) {
                    "Transaksi yang ditolak akan dihapus dari perangkat dan tidak dapat dipulihkan."
                } else {
                    "Pastikan transaksi server sudah diperiksa. Konfirmasi akan menghapus peringatan lokal."
                },
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Batal")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = if (deleting) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (deleting) "Hapus" else "Konfirmasi")
            }
        },
    )
}

internal fun offlineRecoveryStatusLabel(
    failureType: OfflineTransactionFailureType?,
): String = when (failureType) {
    OfflineTransactionFailureType.RETRYABLE -> "Menunggu koneksi"
    OfflineTransactionFailureType.REJECTED -> "Transaksi ditolak"
    OfflineTransactionFailureType.RECONCILIATION_REQUIRED -> "Perlu pemeriksaan"
    null -> "Status tidak dikenal"
}

internal fun offlineRecoveryDescription(transaction: OfflineTransaction): String =
    when (transaction.failureType) {
        OfflineTransactionFailureType.RETRYABLE ->
            "Sistem akan mencoba lagi secara otomatis saat koneksi tersedia."
        OfflineTransactionFailureType.REJECTED ->
            offlineRecoveryErrorMessage(transaction.lastError)
        OfflineTransactionFailureType.RECONCILIATION_REQUIRED ->
            "Transaksi sudah tersimpan di server, tetapi nilainya berbeda dari checkout. Jangan coba ulang."
        null -> "Transaksi ini tidak dapat diproses."
    }

internal fun formatOfflineTransactionDate(
    epochMillis: Long,
    formatter: DateTimeFormatter,
): String = runCatching {
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}.getOrDefault("Waktu tidak tersedia")

private sealed interface RecoveryConfirmation {
    val transaction: OfflineTransaction

    data class Delete(override val transaction: OfflineTransaction) : RecoveryConfirmation
    data class Acknowledge(override val transaction: OfflineTransaction) : RecoveryConfirmation
}
