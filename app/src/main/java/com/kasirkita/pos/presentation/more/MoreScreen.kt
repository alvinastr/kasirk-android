package com.kasirkita.pos.presentation.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.presentation.authv2.LogoutState
import com.kasirkita.pos.presentation.authv2.LogoutViewModel
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.sectionTitle
import com.kasirkita.pos.ui.theme.screenTitle
import com.kasirkita.pos.ui.theme.supporting

internal data class MoreDestination(
    val title: String,
    val icon: ImageVector,
    val routeKey: MoreRouteKey,
)

internal data class MoreSection(
    val title: String,
    val destinations: List<MoreDestination>,
)

internal enum class MoreRouteKey {
    ProductManagement,
    Categories,
    Modifiers,
    Shift,
    Printer,
    ReceiptTemplate,
    OfflineRecovery,
    HeldOrders,
}

internal fun moreSectionsFor(role: UserRole): List<MoreSection> = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> listOf(
        MoreSection(
            title = "Katalog",
            destinations = listOf(
                MoreDestination("Kelola Produk", Icons.Outlined.Inventory2, MoreRouteKey.ProductManagement),
                MoreDestination("Kategori", Icons.Outlined.Category, MoreRouteKey.Categories),
                MoreDestination("Modifier", Icons.Outlined.Tune, MoreRouteKey.Modifiers),
            ),
        ),
        MoreSection(
            title = "Operasional",
            destinations = listOf(
                MoreDestination("Shift", Icons.Outlined.MoreTime, MoreRouteKey.Shift),
                MoreDestination("Pemulihan Offline", Icons.Outlined.SettingsBackupRestore, MoreRouteKey.OfflineRecovery),
            ),
        ),
        MoreSection(
            title = "Perangkat & Struk",
            destinations = listOf(
                MoreDestination("Printer", Icons.Outlined.Print, MoreRouteKey.Printer),
                MoreDestination("Template Struk", Icons.Outlined.ReceiptLong, MoreRouteKey.ReceiptTemplate),
            ),
        ),
    )
    UserRole.CASHIER -> listOf(
        MoreSection(
            title = "Operasional",
            destinations = listOf(
                MoreDestination("Shift", Icons.Outlined.MoreTime, MoreRouteKey.Shift),
                MoreDestination("Pesanan Tersimpan", Icons.Outlined.ReceiptLong, MoreRouteKey.HeldOrders),
                MoreDestination("Pemulihan Offline", Icons.Outlined.SettingsBackupRestore, MoreRouteKey.OfflineRecovery),
            ),
        ),
    )
}

internal fun moreDestinationsFor(role: UserRole): List<MoreDestination> =
    moreSectionsFor(role).flatMap { it.destinations }

@Composable
internal fun MoreScreen(
    role: UserRole,
    onDestinationClick: (MoreRouteKey) -> Unit,
    onLogoutComplete: () -> Unit,
    logoutViewModel: LogoutViewModel = hiltViewModel(),
) {
    val logoutState by logoutViewModel.state.collectAsState()

    LaunchedEffect(logoutState) {
        if (logoutState == LogoutState.LoggedOut) {
            logoutViewModel.acknowledgeLoggedOut()
            onLogoutComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(KasirSpacing.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall)) {
                Text(
                    text = "Lainnya",
                    style = MaterialTheme.typography.screenTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Operasional dan pengaturan sesuai akses Anda",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            moreSectionsFor(role)
                .filter { it.destinations.isNotEmpty() }
                .forEach { section ->
                    KasirCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        section.destinations.forEach { destination ->
                            MoreDestinationRow(
                                destination = destination,
                                onClick = { onDestinationClick(destination.routeKey) },
                            )
                        }
                    }
                }

            Column(verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall)) {
                Text(
                    text = "Akun",
                    style = MaterialTheme.typography.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                KasirSecondaryButton(
                    text = if (logoutState == LogoutState.Loading) "Keluar..." else "Keluar",
                    onClick = logoutViewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = logoutState != LogoutState.Loading,
                    destructive = true,
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
    }
}

@Composable
private fun MoreDestinationRow(
    destination: MoreDestination,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KasirSpacing.TouchTarget)
            .clip(RoundedCornerShape(KasirSpacing.CornerRadius))
            .clickable(onClick = onClick)
            .padding(vertical = KasirSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = destination.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = "Buka ${destination.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
