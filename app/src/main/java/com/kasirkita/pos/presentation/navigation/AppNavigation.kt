package com.kasirkita.pos.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.toSessionIdentity
import com.kasirkita.pos.core.network.RefreshTokenCoordinator
import com.kasirkita.pos.core.session.SessionBoundaryCleaner
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.presentation.cart.CartScreen
import com.kasirkita.pos.presentation.cart.CartViewModel
import com.kasirkita.pos.presentation.checkout.CheckoutScreen
import com.kasirkita.pos.presentation.home.HomeScreen
import com.kasirkita.pos.presentation.heldorder.HeldOrdersScreen
import com.kasirkita.pos.presentation.modifier.ModifierGroupScreen
import com.kasirkita.pos.presentation.more.MoreRouteKey
import com.kasirkita.pos.presentation.more.MoreScreen
import com.kasirkita.pos.presentation.offline.OfflineRecoveryScreen
import com.kasirkita.pos.presentation.outlet.OutletScreen
import com.kasirkita.pos.presentation.product.CategoryManagementScreen
import com.kasirkita.pos.presentation.product.CreateProductScreen
import com.kasirkita.pos.presentation.product.EditProductScreen
import com.kasirkita.pos.presentation.product.ProductManagementScreen
import com.kasirkita.pos.presentation.product.ProductScreen
import com.kasirkita.pos.presentation.receipt.ReceiptScreen
import com.kasirkita.pos.presentation.reports.ReportsScreen
import com.kasirkita.pos.presentation.settings.PrinterSettingsScreen
import com.kasirkita.pos.presentation.settings.ReceiptSettingsContext
import com.kasirkita.pos.presentation.settings.ReceiptTemplateSettingsScreen
import com.kasirkita.pos.presentation.shift.ShiftScreen
import com.kasirkita.pos.presentation.stock.StockAdjustmentScreen
import com.kasirkita.pos.presentation.transaction.TransactionDetailScreen
import com.kasirkita.pos.presentation.transaction.TransactionHistoryScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun AppNavigation(
    viewModel: AppNavigationViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val selectedOutlet by viewModel.selectedOutlet.collectAsState()
    val currentShift by viewModel.currentShift.collectAsState()

    when (val currentState = sessionState) {
        SessionState.Checking -> SessionLoadingContent()
        is SessionState.Authenticated,
        SessionState.Unauthenticated,
        -> {
            val navController = rememberNavController()
            val onHeldOrders = heldOrdersNavigationAction { destination ->
                navController.navigate(destination.route) {
                    launchSingleTop = destination.launchSingleTop
                }
            }
            val authenticatedSession = (currentState as? SessionState.Authenticated)?.session
            val productManagementRoute = authenticatedSession
                ?.role
                ?.let(::productManagementRouteFor)
            val categoryManagementRoute = authenticatedSession
                ?.role
                ?.let(::categoryManagementRouteFor)
            val productCreateRoute = authenticatedSession
                ?.role
                ?.let(::productCreateRouteFor)
            val productEditRoute = authenticatedSession
                ?.role
                ?.let(::productEditRouteFor)
            val stockAdjustmentRoute = authenticatedSession
                ?.role
                ?.let(::stockAdjustmentRouteFor)
            val shiftRoute = authenticatedSession
                ?.role
                ?.let(::shiftRouteFor)
            val shiftManageRoute = authenticatedSession
                ?.role
                ?.let(::shiftManageRouteFor)
            val transactionHistoryRoute = authenticatedSession
                ?.role
                ?.let(::transactionHistoryRouteFor)
            val transactionDetailRoute = authenticatedSession
                ?.role
                ?.let(::transactionDetailRouteFor)
            val reportsRoute = authenticatedSession
                ?.role
                ?.let(::reportsRouteFor)
            val printerSettingsRoute = authenticatedSession
                ?.role
                ?.let(::printerSettingsRouteFor)
            val receiptTemplateSettingsRoute = authenticatedSession
                ?.role
                ?.let(::receiptTemplateSettingsRouteFor)
            val startDestination = startupRouteFor(currentState)
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentBackStackEntry?.destination?.route
            val role = authenticatedSession?.role

            AdaptiveNavigationScaffold(
                role = role,
                currentRoute = currentRoute,
                onDestinationSelected = { route ->
                    role?.takeIf { isRouteAuthorizedForRole(route, it) }?.let {
                        navigateToTopLevelDestination(navController, route)
                    }
                },
            ) { contentModifier ->
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = contentModifier,
                ) {
                composable(AuthV2Screen.Graph.route) {
                    AuthV2Navigation(
                        onAuthenticated = { session ->
                            viewModel.onAuthV2Authenticated(session)
                            navController.navigate(postAuthenticationRoute()) {
                                popUpTo(AuthV2Screen.Graph.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screen.Outlet.route) {
                    OutletScreen(
                        onOutletSelected = {
                            navController.navigate(
                                Screen.Shift.createRoute(Screen.Shift.EntryMode.GATE),
                            ) {
                                popUpTo(Screen.Outlet.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screen.Shift.route) { backStackEntry ->
                    val outlet = selectedOutlet
                    val entryMode = Screen.Shift.parseEntryMode(
                        backStackEntry.arguments?.getString(ENTRY_MODE_ARGUMENT),
                    )
                    if (entryMode == null) {
                        LaunchedEffect(backStackEntry) {
                            if (authenticatedSession == null) {
                                navigateToSessionBoundary(
                                    navController = navController,
                                    route = AuthV2Screen.Graph.route,
                                )
                            } else if (outlet == null) {
                                navigateToSessionBoundary(
                                    navController = navController,
                                    route = Screen.Outlet.route,
                                )
                            } else if (isSafeAuthenticatedBackTarget(navController.previousBackStackEntry?.destination?.route)) {
                                navController.popBackStack()
                            } else {
                                navigateToTopLevelDestination(navController, Screen.Home.route)
                            }
                        }
                        SessionLoadingContent()
                    } else if (shouldRecoverShiftToOutlet(outlet)) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Screen.Outlet.route) {
                                popUpTo(backStackEntry.destination.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        SessionLoadingContent()
                    } else {
                        val outletForShift = checkNotNull(outlet)
                        ShiftScreen(
                            outletId = outletForShift.id,
                            outletName = outletForShift.name,
                            cashierName = (authenticatedSession as? NavigationSession.AuthV2)
                                ?.value
                                ?.userName,
                            entryMode = entryMode,
                            onShiftOpen = {
                                navigateToTopLevelDestination(navController, Screen.Products.route)
                            },
                        )
                    }
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        onProductsClick = {
                            role?.let { currentRole ->
                                if (isRouteAuthorizedForRole(Screen.Products.route, currentRole)) {
                                    navigateToTopLevelDestination(navController, Screen.Products.route)
                                }
                            }
                        },
                        onShiftClick = {
                            shiftRoute?.let { route ->
                                role?.takeIf { isRouteAuthorizedForRole(route, it) }?.let {
                                    navController.navigate(route)
                                }
                            }
                        },
                        currentUserName = (authenticatedSession as? NavigationSession.AuthV2)
                            ?.value
                            ?.userName,
                        currentOutletName = selectedOutlet?.name,
                        currentOutletId = selectedOutlet?.id,
                        currentTenantId = (authenticatedSession as? NavigationSession.AuthV2)?.value?.tenantId,
                        currentUserId = (authenticatedSession as? NavigationSession.AuthV2)?.value?.userId,
                        currentCashierSessionId = currentShift?.id,
                        currentShift = currentShift,
                        role = role,
                        onHeldOrdersClick = onHeldOrders,
                        cartViewModel = cartViewModel,
                    )
                }

                composable(Screen.More.route) {
                    val currentRole = role
                    if (currentRole == null) {
                        SessionLoadingContent()
                    } else {
                        MoreScreen(
                            role = currentRole,
                            onDestinationClick = { destination ->
                                navigateFromMore(
                                    destination = destination,
                                    role = currentRole,
                                    navController = navController,
                                    productManagementRoute = productManagementRoute,
                                    categoryManagementRoute = categoryManagementRoute,
                                    printerSettingsRoute = printerSettingsRoute,
                                    receiptTemplateSettingsRoute = receiptTemplateSettingsRoute,
                                    shiftManageRoute = shiftManageRoute,
                                )
                            },
                            onLogoutComplete = {
                                viewModel.onLoggedOut()
                                navController.navigate(AuthV2Screen.Graph.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }

                composable(Screen.Products.route) {
                    ProductScreen(
                        onCartClick = {
                            navController.navigate(Screen.Cart.route)
                        },
                        onHeldOrders = onHeldOrders,
                        cartViewModel = cartViewModel,
                    )
                }

                composable(Screen.OfflineRecovery.route) {
                    OfflineRecoveryScreen(onBack = navController::popBackStack)
                }

                val manageRoute = productManagementRoute
                val createRoute = productCreateRoute
                val editRoute = productEditRoute
                val stockRoute = stockAdjustmentRoute
                val categoryManageRoute = categoryManagementRoute
                if (
                    manageRoute != null &&
                    createRoute != null &&
                    editRoute != null &&
                    stockRoute != null &&
                    categoryManageRoute != null
                ) {
                    composable(manageRoute) { backStackEntry ->
                        val productCreated by backStackEntry
                            .savedStateHandle
                            .getStateFlow(PRODUCT_CREATED_RESULT_KEY, false)
                            .collectAsState()
                        val productUpdated by backStackEntry
                            .savedStateHandle
                            .getStateFlow(PRODUCT_UPDATED_RESULT_KEY, false)
                            .collectAsState()
                        val stockAdjusted by backStackEntry
                            .savedStateHandle
                            .getStateFlow(STOCK_ADJUSTED_RESULT_KEY, false)
                            .collectAsState()

                        ProductManagementScreen(
                            onAddProduct = {
                                navController.navigate(createRoute)
                            },
                            onEditProduct = { product ->
                                navController.navigate(
                                    Screen.ProductEdit.createRoute(product.id),
                                )
                            },
                            onAdjustStock = { product ->
                                navController.navigate(
                                    Screen.StockAdjustment.createRoute(product.id),
                                )
                            },
                            onManageModifiers = {
                                navController.navigate(Screen.ModifierGroups.route)
                            },
                            onManageCategories = {
                                navController.navigate(categoryManageRoute)
                            },
                            onNavigateBack = navController::popBackStack,
                            productCreated = productCreated,
                            onProductCreatedHandled = {
                                backStackEntry.savedStateHandle[
                                    PRODUCT_CREATED_RESULT_KEY
                                ] = false
                            },
                            productUpdated = productUpdated,
                            onProductUpdatedHandled = {
                                backStackEntry.savedStateHandle[
                                    PRODUCT_UPDATED_RESULT_KEY
                                ] = false
                            },
                            stockAdjusted = stockAdjusted,
                            onStockAdjustedHandled = {
                                backStackEntry.savedStateHandle[
                                    STOCK_ADJUSTED_RESULT_KEY
                                ] = false
                            },
                        )
                    }

                    composable(categoryManageRoute) {
                        CategoryManagementScreen(onNavigateBack = navController::popBackStack)
                    }

                    composable(createRoute) {
                        CreateProductScreen(
                            onCancel = navController::popBackStack,
                            onProductCreated = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(PRODUCT_CREATED_RESULT_KEY, true)
                                navController.popBackStack()
                            },
                        )
                    }

                    composable(Screen.ModifierGroups.route) {
                        ModifierGroupScreen(
                            onNavigateBack = navController::popBackStack,
                        )
                    }

                    composable(editRoute) { backStackEntry ->
                        val productId = requireNotNull(
                            backStackEntry.arguments?.getString(PRODUCT_ID_ARGUMENT),
                        )

                        EditProductScreen(
                            productId = productId,
                            onCancel = navController::popBackStack,
                            onProductUpdated = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(PRODUCT_UPDATED_RESULT_KEY, true)
                                navController.popBackStack()
                            },
                        )
                    }

                    composable(stockRoute) { backStackEntry ->
                        val productId = requireNotNull(
                            backStackEntry.arguments?.getString(PRODUCT_ID_ARGUMENT),
                        )

                        StockAdjustmentScreen(
                            productId = productId,
                            onCancel = navController::popBackStack,
                            onAdjustmentSaved = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(STOCK_ADJUSTED_RESULT_KEY, true)
                                navController.popBackStack()
                            },
                        )
                    }
                }

                composable(Screen.Cart.route) {
                    CartScreen(
                        onCheckout = {
                            navController.navigate(Screen.Checkout.route)
                        },
                        onHeldOrders = onHeldOrders,
                        viewModel = cartViewModel,
                    )
                }

                composable(Screen.HeldOrders.route) {
                    HeldOrdersScreen(
                        onBack = navController::popBackStack,
                        onOpened = {
                            navController.navigate(Screen.Cart.route) {
                                popUpTo(Screen.HeldOrders.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        viewModel = cartViewModel,
                    )
                }

                composable(Screen.Checkout.route) {
                    CheckoutScreen(
                        onCheckoutSuccess = { transactionId ->
                            cartViewModel.refreshHeldOrders()
                            val destination = receiptDestination(transactionId)
                            navController.navigate(destination.route) {
                                popUpTo(existingSalesFlowPopUpRoute(navController, destination.popUpToRoute)) {
                                    inclusive = destination.popUpToInclusive
                                }
                                launchSingleTop = true
                            }
                        },
                        onBackToHeldOrders = {
                            navController.navigate(Screen.HeldOrders.route) {
                                popUpTo(Screen.Checkout.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screen.Receipt.route) {
                    ReceiptScreen(
                        onNewTransaction = {
                            val destination = newTransactionDestination()
                            navController.navigate(destination.route) {
                                popUpTo(existingSalesFlowPopUpRoute(navController, destination.popUpToRoute)) {
                                    inclusive = destination.popUpToInclusive
                                }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                val historyRoute = transactionHistoryRoute
                val detailRoute = transactionDetailRoute
                if (historyRoute != null && detailRoute != null) {
                    composable(historyRoute) {
                        TransactionHistoryScreen(
                            onBack = navController::popBackStack,
                            onTransactionClick = { transactionId ->
                                navController.navigate(
                                    Screen.TransactionDetail.createRoute(transactionId),
                                )
                            },
                        )
                    }

                    composable(detailRoute) {
                        TransactionDetailScreen(
                            onBack = navController::popBackStack,
                            onViewReceipt = { transactionId ->
                                navController.navigate(
                                    Screen.Receipt.createRoute(transactionId),
                                )
                            },
                        )
                    }
                }

                reportsRoute?.let { route ->
                    composable(route) {
                        ReportsScreen(onBack = navController::popBackStack)
                    }
                }

                printerSettingsRoute?.let { route ->
                    composable(route) {
                        PrinterSettingsScreen(onBack = navController::popBackStack)
                    }
                }
                receiptTemplateSettingsRoute?.let { route ->
                    composable(route) {
                        val session = (authenticatedSession as? NavigationSession.AuthV2)?.value
                        ReceiptTemplateSettingsScreen(
                            context = ReceiptSettingsContext(
                                tenantId = session?.tenantId.orEmpty(),
                                outletId = selectedOutlet?.id ?: session?.outletId,
                                role = session?.role ?: UserRole.CASHIER,
                            ),
                            onBack = navController::popBackStack,
                        )
                    }
                }
            }
            }
        }
    }
}

private fun navigateFromMore(
    destination: MoreRouteKey,
    role: UserRole,
    navController: androidx.navigation.NavHostController,
    productManagementRoute: String?,
    categoryManagementRoute: String?,
    printerSettingsRoute: String?,
    receiptTemplateSettingsRoute: String?,
    shiftManageRoute: String?,
) {
    val route = moreDestinationRouteFor(
        destination = destination,
        productManagementRoute = productManagementRoute,
        categoryManagementRoute = categoryManagementRoute,
        printerSettingsRoute = printerSettingsRoute,
        receiptTemplateSettingsRoute = receiptTemplateSettingsRoute,
        shiftManageRoute = shiftManageRoute,
    ) ?: return

    if (!isRouteAuthorizedForRole(route, role)) return

    navController.navigate(route) {
        launchSingleTop = true
    }
}

internal fun moreDestinationRouteFor(
    destination: MoreRouteKey,
    productManagementRoute: String?,
    categoryManagementRoute: String?,
    printerSettingsRoute: String?,
    receiptTemplateSettingsRoute: String?,
    shiftManageRoute: String?,
): String? = when (destination) {
    MoreRouteKey.ProductManagement -> productManagementRoute
    MoreRouteKey.Categories -> categoryManagementRoute
    MoreRouteKey.Modifiers -> Screen.ModifierGroups.route
    MoreRouteKey.Shift -> shiftManageRoute
    MoreRouteKey.Printer -> printerSettingsRoute
    MoreRouteKey.ReceiptTemplate -> receiptTemplateSettingsRoute
    MoreRouteKey.OfflineRecovery -> Screen.OfflineRecovery.route
    MoreRouteKey.HeldOrders -> Screen.HeldOrders.route
}

internal data class SalesFlowDestination(
    val route: String,
    val popUpToRoute: String,
    val popUpToInclusive: Boolean = false,
)

internal fun receiptDestination(transactionId: String): SalesFlowDestination =
    SalesFlowDestination(
        route = Screen.Receipt.createRoute(transactionId),
        popUpToRoute = Screen.Home.route,
    )

internal fun newTransactionDestination(): SalesFlowDestination =
    SalesFlowDestination(
        route = Screen.Products.route,
        popUpToRoute = Screen.Home.route,
    )

internal fun salesFlowPopUpRoute(hasPreferredRoute: Boolean): String =
    if (hasPreferredRoute) Screen.Home.route else Screen.Products.route

internal data class TopLevelNavigationPlan(
    val operations: List<TopLevelNavigationOperation>,
)

internal data class TopLevelNavigationOperation(
    val route: String,
    val popUpTo: TopLevelPopUpTarget,
    val inclusive: Boolean = false,
    val launchSingleTop: Boolean = true,
    val saveState: Boolean = false,
    val restoreState: Boolean = false,
)

internal enum class TopLevelPopUpTarget {
    NONE,
    HOME,
    AUTHENTICATED_GRAPH,
}

internal fun topLevelNavigationPlan(
    destination: String,
    homePresent: Boolean,
): TopLevelNavigationPlan = if (homePresent) {
    TopLevelNavigationPlan(
        operations = listOf(
            TopLevelNavigationOperation(
                route = destination,
                popUpTo = TopLevelPopUpTarget.HOME,
                saveState = true,
                restoreState = true,
            ),
        ),
    )
} else {
    TopLevelNavigationPlan(
        operations = buildList {
            add(
                TopLevelNavigationOperation(
                    route = Screen.Home.route,
                    popUpTo = TopLevelPopUpTarget.AUTHENTICATED_GRAPH,
                ),
            )
            if (destination != Screen.Home.route) {
                add(
                    TopLevelNavigationOperation(
                        route = destination,
                        popUpTo = TopLevelPopUpTarget.HOME,
                        saveState = true,
                        restoreState = true,
                    ),
                )
            }
        },
    )
}

private fun navigateToTopLevelDestination(
    navController: androidx.navigation.NavHostController,
    destination: String,
) {
    val homePresent = runCatching {
        navController.getBackStackEntry(Screen.Home.route)
    }.isSuccess
    val plan = topLevelNavigationPlan(destination, homePresent)
    plan.operations.forEach { operation ->
        navController.navigate(operation.route) {
            when (operation.popUpTo) {
                TopLevelPopUpTarget.NONE -> Unit
                TopLevelPopUpTarget.HOME -> popUpTo(Screen.Home.route) {
                    inclusive = operation.inclusive
                    saveState = operation.saveState
                }
                TopLevelPopUpTarget.AUTHENTICATED_GRAPH -> popUpTo(navController.graph.id) {
                    inclusive = true
                }
            }
            launchSingleTop = operation.launchSingleTop
            restoreState = operation.restoreState
        }
    }
}

private fun navigateToSessionBoundary(
    navController: androidx.navigation.NavHostController,
    route: String,
) {
    navController.navigate(route) {
        popUpTo(navController.graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

internal fun isSafeAuthenticatedBackTarget(route: String?): Boolean =
    topLevelRouteFor(route) != null

private fun existingSalesFlowPopUpRoute(
    navController: androidx.navigation.NavHostController,
    preferredRoute: String,
): String = runCatching {
    navController.getBackStackEntry(preferredRoute)
}.let { result -> salesFlowPopUpRoute(result.isSuccess) }

internal data class HeldOrdersNavigationDestination(
    val route: String,
    val launchSingleTop: Boolean,
)

internal fun heldOrdersNavigationAction(
    navigate: (HeldOrdersNavigationDestination) -> Unit,
): () -> Unit = {
    navigate(
        HeldOrdersNavigationDestination(
            route = Screen.HeldOrders.route,
            launchSingleTop = true,
        ),
    )
}

internal fun shiftRouteFor(role: UserRole): String = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    UserRole.CASHIER,
    -> Screen.Shift.createRoute(Screen.Shift.EntryMode.GATE)
}

internal fun shiftManageRouteFor(role: UserRole): String = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    UserRole.CASHIER,
    -> Screen.Shift.createRoute(Screen.Shift.EntryMode.MANAGE)
}

internal fun productManagementRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.ProductManagement.route
    UserRole.CASHIER -> null
}

internal fun categoryManagementRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.CategoryManagement.route
    UserRole.CASHIER -> null
}

internal fun productCreateRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.ProductCreate.route
    UserRole.CASHIER -> null
}

internal fun productEditRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.ProductEdit.route
    UserRole.CASHIER -> null
}

internal fun stockAdjustmentRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.StockAdjustment.route
    UserRole.CASHIER -> null
}

internal fun transactionHistoryRouteFor(role: UserRole): String = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    UserRole.CASHIER,
    -> Screen.Transactions.route
}

internal fun transactionDetailRouteFor(role: UserRole): String = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    UserRole.CASHIER,
    -> Screen.TransactionDetail.route
}

internal fun reportsRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.Reports.route
    UserRole.CASHIER -> null
}

internal fun printerSettingsRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.PrinterSettings.route
    UserRole.CASHIER -> null
}

internal fun receiptTemplateSettingsRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.ReceiptTemplateSettings.route
    UserRole.CASHIER -> null
}

internal const val PRODUCT_CREATED_RESULT_KEY = "product_created"
internal const val PRODUCT_UPDATED_RESULT_KEY = "product_updated"
internal const val STOCK_ADJUSTED_RESULT_KEY = "stock_adjusted"
private const val PRODUCT_ID_ARGUMENT = "productId"
private const val ENTRY_MODE_ARGUMENT = "entryMode"

@Composable
private fun SessionLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

sealed interface SessionState {
    data object Checking : SessionState
    data class Authenticated(
        val session: NavigationSession,
        val requiresOperationalSetup: Boolean,
    ) : SessionState
    data object Unauthenticated : SessionState
}

sealed interface NavigationSession {
    val role: UserRole

    data class AuthV2(val value: AuthSession) : NavigationSession {
        override val role: UserRole = value.role
    }
}

internal fun startupRouteFor(state: SessionState): String = when (state) {
    is SessionState.Authenticated -> {
        if (state.requiresOperationalSetup) Screen.Outlet.route
        else Screen.Home.route
    }
    SessionState.Checking,
    SessionState.Unauthenticated,
    -> AuthV2Screen.Graph.route
}

internal fun postAuthenticationRoute(): String = Screen.Outlet.route

internal fun shouldRecoverShiftToOutlet(selectedOutlet: Outlet?): Boolean = selectedOutlet == null

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val authSessionDataStore: AuthSessionDataStore,
    private val refreshTokenCoordinator: RefreshTokenCoordinator,
    private val outletRepository: OutletRepository,
    private val shiftRepository: ShiftRepository,
    private val sessionBoundaryCleaner: SessionBoundaryCleaner,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Checking)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    val selectedOutlet: StateFlow<Outlet?> = outletRepository.selectedOutlet
    val currentShift = shiftRepository.currentShift

    init {
        checkSession()
        observeSessionBoundaries()
    }

    fun onAuthV2Authenticated(session: AuthSession) {
        _sessionState.value = SessionState.Authenticated(
            NavigationSession.AuthV2(session),
            requiresOperationalSetup = true,
        )
    }

    fun onLoggedOut() {
        _sessionState.value = SessionState.Unauthenticated
    }

    private fun checkSession() {
        viewModelScope.launch {
            val resolvedState = resolveStartupSession()
            val session = (
                (resolvedState as? SessionState.Authenticated)
                    ?.session as? NavigationSession.AuthV2
                )?.value
            if (session == null) {
                _sessionState.value = resolvedState
                return@launch
            }

            restoreOperationalContextLocally(session)
            _sessionState.value = resolvedState
            refreshSessionInBackground(session)
            refreshSelectedOutletInBackground(session)
            refreshCurrentShiftInBackground(session)
        }
    }

    private suspend fun resolveStartupSession(): SessionState {
        val authV2Session = readAuthV2Session()

        return authV2Session
            ?.let { session -> resolveAuthV2Session(session) }
            ?: SessionState.Unauthenticated
    }

    private suspend fun resolveAuthV2Session(session: AuthSession): SessionState {
        if (session.expiresAt <= System.currentTimeMillis()) {
            sessionBoundaryCleaner.clear(
                tenantId = session.tenantId,
                userId = session.userId,
            )
            clearAuthV2Session(session)
            return SessionState.Unauthenticated
        }
        return SessionState.Authenticated(
            NavigationSession.AuthV2(session),
            requiresOperationalSetup = false,
        )
    }

    private suspend fun restoreOperationalContextLocally(session: AuthSession) {
        try {
            outletRepository.restoreSelectedOutlet()
            val restoredOutletId = outletRepository.selectedOutlet.value?.id
                ?: session.outletId?.takeIf(String::isNotBlank)
            shiftRepository.restoreCurrentShift(expectedOutletId = restoredOutletId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Startup restoration must never crash or block Home
        }
    }

    private fun refreshSessionInBackground(session: AuthSession) {
        viewModelScope.launch {
            val refreshedAccessToken = try {
                refreshTokenCoordinator.refreshAccessToken(session.accessToken)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            if (refreshedAccessToken == null) return@launch

            val refreshedSession = readAuthV2Session() ?: return@launch
            if (refreshedSession.toSessionIdentity() == session.toSessionIdentity()) {
                val currentState = _sessionState.value as? SessionState.Authenticated
                _sessionState.value = SessionState.Authenticated(
                    NavigationSession.AuthV2(refreshedSession),
                    requiresOperationalSetup = currentState?.requiresOperationalSetup ?: false,
                )
            }
        }
    }

    private fun refreshCurrentShiftInBackground(session: AuthSession) {
        viewModelScope.launch {
            shiftRepository.getCurrentShift()
            val currentIdentity = readAuthV2Session()?.toSessionIdentity()
            if (currentIdentity != session.toSessionIdentity()) {
                shiftRepository.clearCurrentShift(session.tenantId, session.userId)
            }
        }
    }

    private fun refreshSelectedOutletInBackground(session: AuthSession) {
        val outletId = session.outletId ?: return
        viewModelScope.launch {
            val outlet = outletRepository.getOutlets()
                .getOrNull()
                ?.firstOrNull { candidate ->
                    candidate.id == outletId && candidate.tenantId == session.tenantId
                }
                ?: return@launch
            val currentIdentity = readAuthV2Session()?.toSessionIdentity()
            if (currentIdentity == session.toSessionIdentity()) {
                outletRepository.selectOutlet(outlet)
            }
        }
    }

    private suspend fun readAuthV2Session(): AuthSession? = try {
        authSessionDataStore.getSession()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private fun observeSessionBoundaries() {
        viewModelScope.launch {
            var previousIdentity = authSessionDataStore.getSession()?.toSessionIdentity()
            var hasPreviousIdentity = previousIdentity != null

            authSessionDataStore.sessionFlow
                .map { session -> session?.toSessionIdentity() }
                .distinctUntilChanged()
                .collect { identity ->
                    val crossedBoundary = hasPreviousIdentity && identity != previousIdentity
                    if (identity == null || crossedBoundary) {
                        sessionBoundaryCleaner.clear(
                            tenantId = previousIdentity?.tenantId,
                            userId = previousIdentity?.userId,
                        )
                    }
                    if (identity == null) {
                        _sessionState.value = SessionState.Unauthenticated
                    }
                    previousIdentity = identity
                    hasPreviousIdentity = true
                }
        }
    }

    private suspend fun clearAuthV2Session(session: AuthSession) {
        try {
            authSessionDataStore.clearSessionIfMatches(
                expectedIdentity = session.toSessionIdentity(),
                expectedRefreshToken = session.refreshToken,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Unit
        }
    }
}
