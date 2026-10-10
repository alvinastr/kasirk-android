package com.kasirkita.pos.presentation.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.ShiftProductSummary
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.presentation.navigation.Screen
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.components.StatusBadge
import com.kasirkita.pos.ui.components.StatusBadgeTone
import com.kasirkita.pos.ui.theme.KasirSpacing
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ShiftScreen(
    outletId: String,
    outletName: String = outletId,
    cashierName: String? = null,
    entryMode: Screen.Shift.EntryMode = Screen.Shift.EntryMode.GATE,
    onShiftOpen: () -> Unit = {},
    viewModel: ShiftViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val loadedShift = (state as? ShiftState.ShiftLoaded)?.shift
    val shouldNavigateToHome = shouldNavigateToHomeFromShift(
        entryMode = entryMode,
        selectedOutletId = outletId,
        shift = loadedShift,
    )
    LaunchedEffect(shouldNavigateToHome) {
        if (shouldNavigateToHome) {
            onShiftOpen()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.shiftOpened.collect {
            onShiftOpen()
        }
    }

    ShiftManagementContent(
        state = state,
        outletId = outletId,
        outletName = outletName,
        cashierName = cashierName,
        onOpen = viewModel::openShift,
        onReviewClose = viewModel::loadSummaryForClose,
        onConfirmClose = viewModel::confirmCloseShift,
        onRetry = viewModel::loadCurrentShift,
        onStartNewShift = viewModel::startNewShift,
    )
}

@Composable
internal fun ShiftManagementContent(
    state: ShiftState,
    outletId: String,
    outletName: String,
    cashierName: String?,
    onOpen: (String) -> Unit,
    onReviewClose: () -> Unit,
    onConfirmClose: () -> Unit,
    onRetry: () -> Unit,
    onStartNewShift: () -> Unit,
) {
    when (state) {
        ShiftState.Loading -> LoadingShiftContent("Memuat shift...")
        ShiftState.NoShift -> OpenShiftContent(
            outletId = outletId,
            outletName = outletName,
            cashierName = cashierName,
            isSubmitting = false,
            onOpen = onOpen,
        )
        ShiftState.Opening -> OpenShiftContent(
            outletId = outletId,
            outletName = outletName,
            cashierName = cashierName,
            isSubmitting = true,
            onOpen = onOpen,
        )
        is ShiftState.ShiftLoaded -> ActiveShiftContent(
            shift = state.shift,
            outletName = outletName,
            summaryError = state.summaryError,
            isSubmitting = false,
            onReviewClose = onReviewClose,
        )
        is ShiftState.LoadingSummary -> LoadingSummaryContent(
            shift = state.shift,
            outletName = outletName,
        )
        is ShiftState.SummaryLoaded -> SummaryReviewContent(
            shift = state.shift,
            summary = state.summary,
            outletName = outletName,
            closeError = state.closeError,
            isSubmitting = false,
            onConfirmClose = onConfirmClose,
        )
        is ShiftState.Closing -> SummaryReviewContent(
            shift = state.shift,
            summary = state.summary,
            outletName = outletName,
            closeError = null,
            isSubmitting = true,
            onConfirmClose = onConfirmClose,
        )
        is ShiftState.ShiftClosed -> ClosedShiftContent(
            shift = state.shift,
            summary = state.summary,
            outletName = outletName,
            onStartNewShift = onStartNewShift,
        )
        is ShiftState.Error -> ShiftErrorContent(
            message = state.message,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun LoadingShiftContent(message: String) {
    KasirLoadingState(
        message = message,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OpenShiftContent(
    outletId: String,
    outletName: String,
    cashierName: String?,
    isSubmitting: Boolean,
    onOpen: (String) -> Unit,
) {
    ShiftContentColumn {
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Kasir belum dibuka",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Buka sesi kasir untuk outlet ini sebelum menerima transaksi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        ShiftValue(label = "Outlet", value = outletName)
        if (!cashierName.isNullOrBlank()) {
            ShiftValue(label = "Kasir", value = cashierName)
        }

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        KasirPrimaryButton(
            text = "Buka Kasir",
            onClick = { onOpen(outletId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            isLoading = isSubmitting,
        )
    }
}

@Composable
private fun ActiveShiftContent(
    shift: Shift,
    outletName: String,
    summaryError: String?,
    isSubmitting: Boolean,
    onReviewClose: () -> Unit,
) {
    ShiftContentColumn {
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Kasir Aktif",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Transaksi akan ditautkan ke sesi kasir ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        ShiftValue(label = "Outlet", value = outletName)
        ShiftValue(label = "Dibuka", value = formatShiftTime(shift.openedAt))
        ShiftStatusBadge(shift.status)

        if (summaryError != null) {
            KasirCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ringkasan belum bisa dimuat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tutup Kasir",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Tinjau ringkasan penjualan dari server sebelum menutup kasir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        KasirPrimaryButton(
            text = "Tinjau Ringkasan",
            onClick = onReviewClose,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            isLoading = isSubmitting,
        )
    }
}

@Composable
private fun LoadingSummaryContent(
    shift: Shift,
    outletName: String,
) {
    ShiftContentColumn {
        ActiveShiftHeader(shift = shift, outletName = outletName)
        LoadingShiftContent("Memuat ringkasan shift...")
    }
}

@Composable
private fun SummaryReviewContent(
    shift: Shift,
    summary: ShiftSummary,
    outletName: String,
    closeError: String?,
    isSubmitting: Boolean,
    onConfirmClose: () -> Unit,
) {
    var showConfirmation by rememberSaveable(shift.id) { mutableStateOf(false) }

    ShiftContentColumn {
        ActiveShiftHeader(shift = shift, outletName = outletName)
        SummaryCard(summary)

        if (closeError != null) {
            KasirCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Kasir belum ditutup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = closeError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        KasirPrimaryButton(
            text = "Konfirmasi Tutup Kasir",
            onClick = { showConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            isLoading = isSubmitting,
        )
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showConfirmation = false },
            title = { Text("Tutup kasir?") },
            text = {
                Text("Sesi kasir akan ditutup menggunakan ringkasan penjualan dari server. Tindakan ini tidak meminta kas akhir.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        onConfirmClose()
                    },
                    enabled = !isSubmitting,
                ) {
                    Text("Tutup Kasir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmation = false },
                    enabled = !isSubmitting,
                ) {
                    Text("Batal")
                }
            },
        )
    }
}

@Composable
private fun ClosedShiftContent(
    shift: Shift,
    summary: ShiftSummary,
    outletName: String,
    onStartNewShift: () -> Unit,
) {
    ShiftContentColumn {
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Kasir Ditutup",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sesi kasir sudah selesai. Ringkasan diambil dari server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ShiftValue(label = "Outlet", value = outletName)
        ShiftValue(label = "Ditutup", value = shift.closedAt?.let(::formatShiftTime) ?: "—")
        ShiftStatusBadge(shift.status)
        SummaryCard(summary)

        KasirSecondaryButton(
            text = "Buka Kasir Baru",
            onClick = onStartNewShift,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActiveShiftHeader(
    shift: Shift,
    outletName: String,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Kasir Aktif",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tinjau ringkasan sebelum menutup kasir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ShiftValue(label = "Outlet", value = outletName)
    ShiftValue(label = "Dibuka", value = formatShiftTime(shift.openedAt))
    ShiftStatusBadge(shift.status)
}

@Composable
private fun SummaryCard(summary: ShiftSummary) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Ringkasan Penjualan",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ShiftValueInline(label = "Transaksi", value = summary.transactionCount.toString())
        ShiftMoneyInline(label = "Total Penjualan", amount = summary.totals.sales)
        ShiftMoneyInline(label = "Tunai", amount = summary.totals.cash)
        ShiftMoneyInline(label = "QRIS", amount = summary.totals.qris)
        ShiftMoneyInline(label = "EDC", amount = summary.totals.edc)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Produk Terjual",
            style = MaterialTheme.typography.titleMedium,
        )
        if (summary.products.isEmpty()) {
            Text(
                text = "Belum ada produk terjual.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            summary.products.forEach { product ->
                ProductSummaryRow(product)
            }
        }
    }
}

@Composable
private fun ProductSummaryRow(product: ShiftProductSummary) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = product.productName,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${product.quantity} terjual",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShiftMoneyInline(label: String, amount: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PriceText(text = formatShiftMoney(amount))
    }
}

@Composable
private fun ShiftValueInline(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ShiftErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    KasirErrorState(
        message = message,
        onRetry = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ShiftContentColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KasirSpacing.ScreenPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
private fun ShiftValue(
    label: String,
    value: String,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ShiftStatusBadge(status: String) {
    StatusBadge(
        text = when {
            status.equals("OPEN", ignoreCase = true) -> "Kasir aktif"
            status.equals("CLOSED", ignoreCase = true) -> "Kasir ditutup"
            else -> status
        },
        tone = if (status.equals("OPEN", ignoreCase = true)) {
            StatusBadgeTone.Success
        } else {
            StatusBadgeTone.Neutral
        },
    )
}

internal fun formatShiftMoney(amount: Long?): String {
    if (amount == null) return "—"
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    return if (amount < 0) {
        "-Rp${formatter.format(-amount)}"
    } else {
        "Rp${formatter.format(amount)}"
    }
}

internal fun formatShiftTime(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofPattern(
                "dd MMM yyyy, HH.mm",
                Locale.forLanguageTag("id-ID"),
            ),
        )
}.getOrDefault(value)

internal fun shouldNavigateToHomeFromShift(
    entryMode: Screen.Shift.EntryMode,
    selectedOutletId: String,
    shift: Shift?,
): Boolean = entryMode == Screen.Shift.EntryMode.GATE && shift?.let { activeShift ->
    activeShift.status.equals("OPEN", ignoreCase = true) &&
        activeShift.outletId == selectedOutletId
} == true
