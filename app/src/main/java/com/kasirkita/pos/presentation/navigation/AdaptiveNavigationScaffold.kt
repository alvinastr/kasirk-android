package com.kasirkita.pos.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.kasirkita.pos.domain.model.UserRole

internal data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

internal fun topLevelDestinationsFor(role: UserRole): List<TopLevelDestination> = buildList {
    add(TopLevelDestination(Screen.Home.route, "Beranda", Icons.Outlined.Home))
    add(TopLevelDestination(Screen.Products.route, "Kasir", Icons.Outlined.PointOfSale))
    add(TopLevelDestination(Screen.Transactions.route, "Transaksi", Icons.Outlined.ReceiptLong))
    if (role == UserRole.OWNER || role == UserRole.ADMIN) {
        add(TopLevelDestination(Screen.Reports.route, "Laporan", Icons.Outlined.BarChart))
    }
    add(TopLevelDestination(Screen.More.route, "Lainnya", Icons.Outlined.MoreHoriz))
}

internal fun topLevelRouteFor(route: String?): String? = when (route) {
    Screen.Home.route -> Screen.Home.route
    Screen.Products.route,
    Screen.Cart.route,
    Screen.HeldOrders.route,
    Screen.Checkout.route,
    -> Screen.Products.route
    Screen.Transactions.route,
    Screen.TransactionDetail.route,
    -> Screen.Transactions.route
    Screen.Reports.route -> Screen.Reports.route
    Screen.More.route,
    Screen.ProductManagement.route,
    Screen.CategoryManagement.route,
    Screen.ModifierGroups.route,
    Screen.PrinterSettings.route,
    Screen.ReceiptTemplateSettings.route,
    Screen.OfflineRecovery.route,
    -> Screen.More.route
    else -> null
}

internal fun shouldShowPersistentNavigation(route: String?): Boolean = route in setOf(
    Screen.Home.route,
    Screen.Products.route,
    Screen.Transactions.route,
    Screen.Reports.route,
    Screen.More.route,
)

internal fun usesTabletNavigation(widthDp: Int): Boolean = widthDp >= 840

internal enum class NavigationChrome {
    Hidden,
    BottomBar,
    Rail,
}

internal fun navigationChromeFor(
    role: UserRole?,
    route: String?,
    widthDp: Int,
): NavigationChrome = when {
    role == null || !shouldShowPersistentNavigation(route) -> NavigationChrome.Hidden
    usesTabletNavigation(widthDp) -> NavigationChrome.Rail
    else -> NavigationChrome.BottomBar
}

internal fun isRouteAuthorizedForRole(route: String, role: UserRole): Boolean = when {
    route in setOf(
        Screen.Home.route,
        Screen.Products.route,
        Screen.Cart.route,
        Screen.HeldOrders.route,
        Screen.Checkout.route,
        Screen.Receipt.route,
        Screen.Transactions.route,
        Screen.TransactionDetail.route,
        Screen.OfflineRecovery.route,
        Screen.More.route,
    ) -> true
    Screen.Shift.parseRoute(route) != null -> true
    route.startsWith("receipt/") || route.startsWith("transactions/") -> true
    route.startsWith("products/manage/") -> role == UserRole.OWNER || role == UserRole.ADMIN
    route == Screen.Reports.route ||
        route == Screen.ProductManagement.route ||
        route == Screen.ProductCreate.route ||
        route == Screen.ProductEdit.route ||
        route == Screen.StockAdjustment.route ||
        route == Screen.CategoryManagement.route ||
        route == Screen.ModifierGroups.route ||
        route == Screen.PrinterSettings.route ||
        route == Screen.ReceiptTemplateSettings.route -> role == UserRole.OWNER || role == UserRole.ADMIN
    else -> false
}

@Composable
internal fun AdaptiveNavigationScaffold(
    role: UserRole?,
    currentRoute: String?,
    onDestinationSelected: (String) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        val destinations = role?.let(::topLevelDestinationsFor).orEmpty()
        val selectedRoute = topLevelRouteFor(currentRoute)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val resolvedChrome = navigationChromeFor(role, currentRoute, maxWidth.value.toInt())
            when (resolvedChrome) {
                NavigationChrome.Hidden -> content(Modifier.fillMaxSize())
                NavigationChrome.Rail -> Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                selected = destination.route == selectedRoute,
                                onClick = { onDestinationSelected(destination.route) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        content(Modifier.fillMaxSize())
                    }
                }
                NavigationChrome.BottomBar -> Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            destinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = destination.route == selectedRoute,
                                    onClick = { onDestinationSelected(destination.route) },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    },
                ) { paddingValues ->
                    content(Modifier.fillMaxSize().padding(paddingValues))
                }
            }
        }
    }
}
