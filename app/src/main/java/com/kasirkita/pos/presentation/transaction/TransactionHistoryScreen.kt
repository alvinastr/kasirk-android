package com.kasirkita.pos.presentation.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.theme.KasirSpacing
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
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
    val fromDate by viewModel.fromDate.collectAsState()
    val toDate by viewModel.toDate.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TransactionHistoryContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::loadTransactions,
        onTransactionClick = onTransactionClick,
        fromDate = fromDate,
        toDate = toDate,
        onDateFilterClick = { showDatePicker = true },
        onClearFilter = viewModel::clearDateFilter,
        hasActiveFilter = fromDate != null || toDate != null,
    )

    if (showDatePicker) {
        DateFilterDialog(
            fromDate = fromDate?.let(::utcInstantToLocalDate),
            toDate = toDate?.let(::utcExclusiveEndToLocalDate),
            onConfirm = { from, to ->
                viewModel.setLocalDateFilter(from, to)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
internal fun TransactionHistoryContent(
    state: TransactionHistoryState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTransactionClick: (String) -> Unit,
    fromDate: String?,
    toDate: String?,
    onDateFilterClick: () -> Unit,
    onClearFilter: () -> Unit,
    hasActiveFilter: Boolean,
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
            TransactionHistoryHeader(
                onBack = onBack,
                fromDate = fromDate,
                toDate = toDate,
                onDateFilterClick = onDateFilterClick,
                onClearFilter = onClearFilter,
                hasActiveFilter = hasActiveFilter,
            )

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
private fun TransactionHistoryHeader(
    onBack: () -> Unit,
    fromDate: String?,
    toDate: String?,
    onDateFilterClick: () -> Unit,
    onClearFilter: () -> Unit,
    hasActiveFilter: Boolean,
) {
    val localFromDate = fromDate?.let(::utcInstantToLocalDate)
    val localToDate = toDate?.let(::utcExclusiveEndToLocalDate)
    val filterText = when {
        localFromDate != null && localToDate != null && localFromDate == localToDate -> "Hari ini ($localFromDate)"
        localFromDate != null && localToDate != null -> "$localFromDate – $localToDate"
        localFromDate != null -> "Dari $localFromDate"
        localToDate != null -> "Sampai $localToDate"
        else -> "Semua waktu"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = KasirSpacing.XSmall),
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KasirSecondaryButton(
                text = "Filter: $filterText",
                onClick = onDateFilterClick,
                modifier = Modifier.weight(1f),
            )
            if (hasActiveFilter) {
                KasirTextButton(
                    text = "Hapus Filter",
                    onClick = onClearFilter,
                )
            }
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
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

@Composable
private fun DateFilterDialog(
    fromDate: String?,
    toDate: String?,
    onConfirm: (String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tempFromDate = remember { mutableStateOf(fromDate) }
    val tempToDate = remember { mutableStateOf(toDate) }
    val context = LocalContext.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Filter Tanggal", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = KasirSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
            ) {
                Text(
                    text = "Pilih rentang tanggal untuk memfilter transaksi",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
                ) {
                    DatePickerButton(
                        label = "Dari",
                        selectedDate = tempFromDate.value,
                        onClick = {
                            showDatePickerDialog(context, "Dari") { selected ->
                                tempFromDate.value = selected
                            }
                        },
                    )
                    DatePickerButton(
                        label = "Sampai",
                        selectedDate = tempToDate.value,
                        onClick = {
                            showDatePickerDialog(context, "Sampai") { selected ->
                                tempToDate.value = selected
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            KasirPrimaryButton(
                text = "Terapkan",
                onClick = { onConfirm(tempFromDate.value, tempToDate.value) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            KasirTextButton(
                text = "Batal",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
fun RowScope.DatePickerButton(
    label: String,
    selectedDate: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedDate ?: "Pilih tanggal",
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun showDatePickerDialog(
    context: android.content.Context,
    title: String,
    onDateSelected: (String) -> Unit,
) {
    val calendar = java.util.Calendar.getInstance()
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            onDateSelected(date)
        },
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH),
        calendar.get(java.util.Calendar.DAY_OF_MONTH),
    )
    datePickerDialog.setTitle(title)
    datePickerDialog.show()
}

/** Converts an ISO-8601 UTC instant to the local calendar date for display. */
internal fun utcInstantToLocalDate(utcInstant: String): String =
    Instant.parse(utcInstant)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

/** Converts an exclusive-end ISO-8601 UTC instant to the inclusive local calendar date for display. */
internal fun utcExclusiveEndToLocalDate(utcInstant: String): String =
    Instant.parse(utcInstant)
        .minusNanos(1)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
