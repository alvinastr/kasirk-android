package com.kasirkita.pos.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.ShiftSummary
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.presentation.cart.CartViewModel
import com.kasirkita.pos.presentation.cart.HeldOrderTotalState
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.PriceDisplay
import com.kasirkita.pos.ui.components.StatusBadge
import com.kasirkita.pos.ui.components.StatusBadgeTone
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.KasirWarning
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.screenTitle
import com.kasirkita.pos.ui.theme.sectionTitle
import com.kasirkita.pos.ui.theme.supporting
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onProductsClick: () -> Unit,
    onShiftClick: () -> Unit,
    currentUserName: String? = null,
    currentOutletName: String? = null,
    currentOutletId: String? = null,
    currentTenantId: String? = null,
    currentUserId: String? = null,
    currentCashierSessionId: String? = null,
    currentShift: Shift? = null,
    role: UserRole? = null,
    onHeldOrdersClick: () -> Unit = {},
    cartViewModel: CartViewModel,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val syncState by viewModel.syncState.collectAsState()
    val dashboardState by viewModel.dashboardState.collectAsState()
    val cartState by cartViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val hasActiveShift = currentShift?.status == "OPEN"

    LaunchedEffect(
        lifecycleOwner,
        role,
        currentOutletId,
        currentShift?.id,
        currentTenantId,
        currentUserId,
        currentCashierSessionId,
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh(
                role = role,
                outletId = currentOutletId,
                shiftId = currentShift?.takeIf { it.status == "OPEN" }?.id,
                tenantId = currentTenantId,
                userId = currentUserId,
                cashierSessionId = currentCashierSessionId,
                force = false,
            )
            cartViewModel.refreshHeldOrders()
            awaitCancellation()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = homeUsesWideLayout(maxWidth.value.toInt())
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (isWide) 960.dp else 1200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(KasirSpacing.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
            ) {
                HomeHeader(userName = currentUserName)

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OperationalContextCard(
                            outletName = currentOutletName,
                            currentShift = currentShift,
                            numberFormat = numberFormat,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryCashierActionCard(
                            hasActiveShift = hasActiveShift,
                            onProductsClick = onProductsClick,
                            onShiftClick = onShiftClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    OperationalContextCard(
                        outletName = currentOutletName,
                        currentShift = currentShift,
                        numberFormat = numberFormat,
                    )
                    PrimaryCashierActionCard(
                        hasActiveShift = hasActiveShift,
                        onProductsClick = onProductsClick,
                        onShiftClick = onShiftClick,
                    )
                }

                DashboardOperationalSummary(
                    role = role,
                    dailySales = dashboardState.dailySales,
                    shiftSummary = dashboardState.shiftSummary,
                    onRetryDaily = { viewModel.retryDaily(role, currentOutletId) },
                    onRetryShift = {
                        viewModel.retryShift(
                            currentOutletId,
                            currentShift?.takeIf { it.status == "OPEN" }?.id,
                        )
                    },
                )

                HeldOrdersSummary(
                    totalState = cartState.heldOrderTotalState,
                    onRetry = cartViewModel::refreshHeldOrders,
                    onOpen = onHeldOrdersClick,
                )

                SyncStatusCard(
                    syncState = syncState,
                    onSyncNow = viewModel::syncNow,
                )
            }
        }
    }
}

@Composable
private fun DashboardOperationalSummary(
    role: UserRole?,
    dailySales: DashboardMetric<com.kasirkita.pos.domain.model.DailySalesReport>,
    shiftSummary: DashboardMetric<ShiftSummary>,
    onRetryDaily: () -> Unit,
    onRetryShift: () -> Unit,
) {
    if (role == UserRole.CASHIER) {
        ShiftSummarySurface(shiftSummary, onRetryShift)
        return
    }
    if (role == UserRole.OWNER || role == UserRole.ADMIN) {
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text("Hari ini", style = MaterialTheme.typography.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
            when (dailySales) {
                DashboardMetric.NotRequested -> Unit
                DashboardMetric.Loading -> Text("Memuat penjualan...", style = MaterialTheme.typography.supporting)
                is DashboardMetric.Success -> {
                    PriceDisplay("Total penjualan", formatRupiah(dailySales.value.totalSales, NumberFormat.getNumberInstance(Locale("id", "ID"))))
                    HomeMetricRow("Transaksi", dailySales.value.transactionCount.toString(), MaterialTheme.colorScheme.onSurface)
                }
                is DashboardMetric.Error -> MetricError(dailySales.message, onRetryDaily)
            }
        }
        ShiftSummarySurface(shiftSummary, onRetryShift)
    }
}

@Composable
private fun ShiftSummarySurface(state: DashboardMetric<ShiftSummary>, onRetry: () -> Unit) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text("Shift aktif", style = MaterialTheme.typography.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
        when (state) {
            DashboardMetric.NotRequested -> Text("Buka shift untuk melihat ringkasan.", style = MaterialTheme.typography.supporting)
            DashboardMetric.Loading -> Text("Memuat ringkasan shift...", style = MaterialTheme.typography.supporting)
            is DashboardMetric.Success -> {
                val summary = state.value
                HomeMetricRow("Transaksi", summary.transactionCount.toString(), MaterialTheme.colorScheme.onSurface)
                HomeMetricRow("Tunai", formatRupiah(summary.totals.cash, NumberFormat.getNumberInstance(Locale("id", "ID"))), MaterialTheme.colorScheme.onSurface)
                HomeMetricRow("QRIS", formatRupiah(summary.totals.qris, NumberFormat.getNumberInstance(Locale("id", "ID"))), MaterialTheme.colorScheme.onSurface)
                HomeMetricRow("EDC", formatRupiah(summary.totals.edc, NumberFormat.getNumberInstance(Locale("id", "ID"))), MaterialTheme.colorScheme.onSurface)
            }
            is DashboardMetric.Error -> MetricError(state.message, onRetry)
        }
    }
}

@Composable
private fun MetricError(message: String, onRetry: () -> Unit) {
    Text(message, style = MaterialTheme.typography.supporting, color = MaterialTheme.colorScheme.error)
    KasirSecondaryButton(text = "Coba lagi", onClick = onRetry, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun HeldOrdersSummary(
    totalState: HeldOrderTotalState,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text("Pesanan tersimpan", style = MaterialTheme.typography.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
        when (val presentation = heldOrderHomePresentation(totalState)) {
            HeldOrderHomePresentation.LOADING -> Text("Memuat jumlah pesanan...", style = MaterialTheme.typography.supporting)
            is HeldOrderHomePresentation.ERROR -> {
                Text("Jumlah pesanan tidak tersedia saat ini.", style = MaterialTheme.typography.supporting, color = MaterialTheme.colorScheme.error)
                KasirSecondaryButton(text = "Coba lagi", onClick = onRetry, modifier = Modifier.fillMaxWidth())
            }
            is HeldOrderHomePresentation.STALE -> {
                Text("Jumlah terakhir: ${presentation.total}. Belum tersinkron.", style = MaterialTheme.typography.supporting, color = MaterialTheme.colorScheme.error)
                KasirSecondaryButton(text = "Coba lagi", onClick = onRetry, modifier = Modifier.fillMaxWidth())
            }
            is HeldOrderHomePresentation.AVAILABLE -> {
                HomeMetricRow("Jumlah", presentation.total.toString(), MaterialTheme.colorScheme.onSurface)
                KasirSecondaryButton(text = "Buka pesanan tersimpan", onClick = onOpen, modifier = Modifier.fillMaxWidth())
            }
            HeldOrderHomePresentation.NOT_LOADED -> Text("Buka shift untuk melihat pesanan tersimpan.", style = MaterialTheme.typography.supporting)
        }
    }
}

internal fun homeUsesWideLayout(widthDp: Int): Boolean = widthDp >= 840

@Composable
private fun HomeHeader(userName: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Text(
            text = "KasirKita POS",
            style = MaterialTheme.typography.screenTitle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        userName?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OperationalContextCard(
    outletName: String?,
    currentShift: Shift?,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier,
) {
    val shiftIsOpen = currentShift?.status == "OPEN"
    KasirCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
            ) {
                Text(
                    text = "Outlet aktif",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = outletName?.takeIf(String::isNotBlank) ?: "Outlet belum dipilih",
                    style = MaterialTheme.typography.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(
                text = if (shiftIsOpen) "Shift aktif" else "Shift belum aktif",
                tone = if (shiftIsOpen) StatusBadgeTone.Success else StatusBadgeTone.Warning,
            )
        }

        currentShift?.let { shift ->
            PriceDisplay(
                label = "Kas awal",
                value = formatRupiah(shift.openingCash, numberFormat),
            )
            tryFormatOpenedAt(shift.openedAt)?.let { openedText ->
                Text(
                    text = "Dibuka $openedText",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PrimaryCashierActionCard(
    hasActiveShift: Boolean,
    onProductsClick: () -> Unit,
    onShiftClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KasirCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (hasActiveShift) "Kasir siap" else "Mulai operasional",
            style = MaterialTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (hasActiveShift) {
                "Pilih produk untuk melayani transaksi."
            } else {
                "Buka shift sebelum menerima transaksi."
            },
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KasirPrimaryButton(
            text = if (hasActiveShift) "Buka Kasir" else "Buka Shift",
            onClick = if (hasActiveShift) onProductsClick else onShiftClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SyncStatusCard(
    syncState: HomeSyncState,
    onSyncNow: () -> Unit,
) {
    val hasPending = syncState.pendingCount > 0
    val hasActionRequired = syncState.actionRequiredCount > 0

    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Status transaksi",
                style = MaterialTheme.typography.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusBadge(
                text = when {
                    hasActionRequired -> "Perlu tindakan"
                    hasPending -> "Menunggu sync"
                    else -> "Aman"
                },
                tone = when {
                    hasActionRequired -> StatusBadgeTone.Error
                    hasPending -> StatusBadgeTone.Warning
                    else -> StatusBadgeTone.Success
                },
            )
        }

        HomeMetricRow(
            label = "Menunggu sinkronisasi",
            value = syncState.pendingCount.toString(),
            valueColor = if (hasPending) KasirWarning else MaterialTheme.colorScheme.onSurface,
        )
        HomeMetricRow(
            label = "Perlu tindakan",
            value = syncState.actionRequiredCount.toString(),
            valueColor = if (hasActionRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )

        syncState.lastResult?.let { result ->
            Text(
                text = "Terakhir: ${result.synced} berhasil, ${result.failed} gagal, ${result.pending} pending",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        syncState.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.error,
            )
        }

        KasirPrimaryButton(
            text = if (syncState.isSyncing) "Sinkronisasi..." else "Sync Sekarang",
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasPending,
            isLoading = syncState.isSyncing,
        )
    }
}

@Composable
private fun HomeMetricRow(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.sectionTitle,
            color = valueColor,
        )
    }
}

private fun formatRupiah(
    amount: Long?,
    numberFormat: NumberFormat,
): String = if (amount == null) "-" else "Rp${numberFormat.format(amount)}"

private fun tryFormatOpenedAt(isoTimestamp: String?): String? {
    if (isoTimestamp.isNullOrBlank()) return null
    return try {
        val instant = Instant.parse(isoTimestamp)
        val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
        val today = LocalDate.now()
        val timeFormatter = DateTimeFormatter.ofPattern("HH.mm")
        val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH.mm", Locale("id", "ID"))

        if (localDateTime.toLocalDate() == today) {
            localDateTime.format(timeFormatter)
        } else {
            localDateTime.format(dateTimeFormatter)
        }
    } catch (_: Exception) {
        null
    }
}
