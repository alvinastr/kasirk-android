package com.kasirkita.pos.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.presentation.authv2.LogoutState
import com.kasirkita.pos.presentation.authv2.LogoutViewModel
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.PriceDisplay
import com.kasirkita.pos.ui.components.StatusBadge
import com.kasirkita.pos.ui.components.StatusBadgeTone
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.KasirSuccess
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
    onCartClick: () -> Unit,
    onShiftClick: () -> Unit,
    onManageProductsClick: (() -> Unit)? = null,
    onTransactionsClick: (() -> Unit)? = null,
    onReportsClick: (() -> Unit)? = null,
    onOfflineProblemsClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    currentUserName: String? = null,
    currentOutletName: String? = null,
    currentShift: Shift? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    logoutViewModel: LogoutViewModel = hiltViewModel(),
) {
    val syncState by viewModel.syncState.collectAsState()
    val logoutState by logoutViewModel.state.collectAsState()
    val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))

    LaunchedEffect(logoutState) {
        if (logoutState == LogoutState.LoggedOut) {
            logoutViewModel.acknowledgeLoggedOut()
            onLogoutComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KasirSpacing.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
    ) {
        HomeHeader(userName = currentUserName)

        OperationalContextCard(
            outletName = currentOutletName,
            currentShift = currentShift,
            numberFormat = numberFormat,
            onShiftClick = onShiftClick,
        )

        PrimaryCashierActionCard(onProductsClick = onProductsClick)

        SyncStatusCard(
            syncState = syncState,
            onSyncNow = viewModel::syncNow,
            onOfflineProblemsClick = onOfflineProblemsClick,
        )

        NavigationActionsCard(
            onProductsClick = onProductsClick,
            onCartClick = onCartClick,
            onShiftClick = onShiftClick,
            onManageProductsClick = onManageProductsClick,
            onTransactionsClick = onTransactionsClick,
            onReportsClick = onReportsClick,
            onOfflineProblemsClick = onOfflineProblemsClick,
        )

        LogoutSection(
            logoutState = logoutState,
            onLogoutClick = logoutViewModel::logout,
        )
    }
}

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
        Text(
            text = userName?.takeIf(String::isNotBlank)?.let { "Siap melayani, $it" } ?: "Dashboard kasir",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OperationalContextCard(
    outletName: String?,
    currentShift: Shift?,
    numberFormat: NumberFormat,
    onShiftClick: () -> Unit,
) {
    val shiftIsOpen = currentShift?.status == "OPEN"
    KasirCard(modifier = Modifier.fillMaxWidth()) {
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
            val openedText = tryFormatOpenedAt(shift.openedAt)
            if (openedText != null) {
                Text(
                    text = "Dibuka $openedText",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } ?: KasirSecondaryButton(
            text = "Buka Shift",
            onClick = onShiftClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrimaryCashierActionCard(onProductsClick: () -> Unit) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Mulai transaksi",
            style = MaterialTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Buka kasir untuk pilih produk dan melayani pembayaran.",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KasirPrimaryButton(
            text = "Buka Kasir",
            onClick = onProductsClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SyncStatusCard(
    syncState: HomeSyncState,
    onSyncNow: () -> Unit,
    onOfflineProblemsClick: () -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            KasirPrimaryButton(
                text = if (syncState.isSyncing) "Sinkronisasi..." else "Sync Sekarang",
                onClick = onSyncNow,
                modifier = Modifier.weight(1f),
                enabled = hasPending,
                isLoading = syncState.isSyncing,
            )
            KasirSecondaryButton(
                text = "Masalah",
                onClick = onOfflineProblemsClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavigationActionsCard(
    onProductsClick: () -> Unit,
    onCartClick: () -> Unit,
    onShiftClick: () -> Unit,
    onManageProductsClick: (() -> Unit)?,
    onTransactionsClick: (() -> Unit)?,
    onReportsClick: (() -> Unit)?,
    onOfflineProblemsClick: () -> Unit,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Operasional",
            style = MaterialTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HomeActionRow(
            primaryText = "Produk",
            onPrimaryClick = onProductsClick,
            secondaryText = "Cart",
            onSecondaryClick = onCartClick,
        )
        HomeActionRow(
            primaryText = "Shift",
            onPrimaryClick = onShiftClick,
            secondaryText = "Masalah Sync",
            onSecondaryClick = onOfflineProblemsClick,
        )
        onTransactionsClick?.let { onClick ->
            HomeActionRow(
                primaryText = "Riwayat Transaksi",
                onPrimaryClick = onClick,
                secondaryText = "Laporan",
                onSecondaryClick = onReportsClick,
            )
        } ?: onReportsClick?.let { onClick ->
            KasirSecondaryButton(
                text = "Laporan",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        onManageProductsClick?.let { onClick ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            KasirSecondaryButton(
                text = "Kelola Produk",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeActionRow(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    secondaryText: String,
    onSecondaryClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        KasirSecondaryButton(
            text = primaryText,
            onClick = onPrimaryClick,
            modifier = Modifier.weight(1f),
        )
        KasirSecondaryButton(
            text = secondaryText,
            onClick = onSecondaryClick ?: {},
            modifier = Modifier.weight(1f),
            enabled = onSecondaryClick != null,
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

@Composable
private fun LogoutSection(
    logoutState: LogoutState,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        KasirSecondaryButton(
            text = if (logoutState == LogoutState.Loading) "Keluar..." else "Keluar",
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = logoutState != LogoutState.Loading,
        )
        (logoutState as? LogoutState.Error)?.let { state ->
            Text(
                text = state.message,
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatRupiah(
    amount: Long?,
    numberFormat: NumberFormat,
): String = if (amount == null) "—" else "Rp${numberFormat.format(amount)}"

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
    } catch (e: Exception) {
        null
    }
}
