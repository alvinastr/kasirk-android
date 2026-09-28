package com.kasirkita.pos.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.network.RefreshTokenCoordinator
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.presentation.cart.CartScreen
import com.kasirkita.pos.presentation.checkout.CheckoutScreen
import com.kasirkita.pos.presentation.home.HomeScreen
import com.kasirkita.pos.presentation.outlet.OutletScreen
import com.kasirkita.pos.presentation.product.CreateProductScreen
import com.kasirkita.pos.presentation.product.EditProductScreen
import com.kasirkita.pos.presentation.product.ProductManagementScreen
import com.kasirkita.pos.presentation.product.ProductScreen
import com.kasirkita.pos.presentation.receipt.ReceiptScreen
import com.kasirkita.pos.presentation.reports.ReportsScreen
import com.kasirkita.pos.presentation.shift.ShiftScreen
import com.kasirkita.pos.presentation.stock.StockAdjustmentScreen
import com.kasirkita.pos.presentation.transaction.TransactionDetailScreen
import com.kasirkita.pos.presentation.transaction.TransactionHistoryScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun AppNavigation(
    viewModel: AppNavigationViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val selectedOutlet by viewModel.selectedOutlet.collectAsState()

    when (val currentState = sessionState) {
        SessionState.Checking -> SessionLoadingContent()
        is SessionState.Authenticated,
        SessionState.Unauthenticated,
        -> {
            val navController = rememberNavController()
            val authenticatedSession = (currentState as? SessionState.Authenticated)?.session
            val productManagementRoute = authenticatedSession
                ?.role
                ?.let(::productManagementRouteFor)
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
            val transactionHistoryRoute = authenticatedSession
                ?.role
                ?.let(::transactionHistoryRouteFor)
            val transactionDetailRoute = authenticatedSession
                ?.role
                ?.let(::transactionDetailRouteFor)
            val reportsRoute = authenticatedSession
                ?.role
                ?.let(::reportsRouteFor)
            val startDestination = startupRouteFor(currentState)

            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(AuthV2Screen.Graph.route) {
                    AuthV2Navigation(
                        onAuthenticated = { session ->
                            viewModel.onAuthV2Authenticated(session)
                            navController.navigate(Screen.Outlet.route) {
                                popUpTo(AuthV2Screen.Graph.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screen.Outlet.route) {
                    OutletScreen(
                        onOutletSelected = {
                            navController.navigate(Screen.Shift.route) {
                                popUpTo(Screen.Outlet.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screen.Shift.route) { backStackEntry ->
                    val outlet = selectedOutlet
                    if (outlet == null) {
                        SessionLoadingContent()
                    } else {
                        val isGateEntry = remember(backStackEntry) {
                            isShiftGateEntry(
                                previousRoute = navController
                                    .previousBackStackEntry
                                    ?.destination
                                    ?.route,
                            )
                        }
                        ShiftScreen(
                            outletId = outlet.id,
                            outletName = outlet.name,
                            autoNavigateToHome = isGateEntry,
                            onShiftOpen = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Shift.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        onProductsClick = {
                            navController.navigate(Screen.Products.route)
                        },
                        onCartClick = {
                            navController.navigate(Screen.Cart.route)
                        },
                        onShiftClick = {
                            shiftRoute?.let(navController::navigate)
                        },
                        onManageProductsClick = productManagementRoute?.let { route ->
                            { navController.navigate(route) }
                        },
                        onTransactionsClick = transactionHistoryRoute?.let { route ->
                            { navController.navigate(route) }
                        },
                        onReportsClick = reportsRoute?.let { route ->
                            { navController.navigate(route) }
                        },
                        onLogoutComplete = {
                            viewModel.onLoggedOut()
                            navController.navigate(AuthV2Screen.Graph.route) {
                                popUpTo(navController.graph.id)
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screen.Products.route) {
                    ProductScreen(
                        onCartClick = {
                            navController.navigate(Screen.Cart.route)
                        },
                    )
                }

                val manageRoute = productManagementRoute
                val createRoute = productCreateRoute
                val editRoute = productEditRoute
                val stockRoute = stockAdjustmentRoute
                if (
                    manageRoute != null &&
                    createRoute != null &&
                    editRoute != null &&
                    stockRoute != null
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
                    )
                }

                composable(Screen.Checkout.route) {
                    CheckoutScreen(
                        onCheckoutSuccess = { transactionId ->
                            navController.navigate(Screen.Receipt.createRoute(transactionId)) {
                                popUpTo(Screen.Checkout.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screen.Receipt.route) {
                    ReceiptScreen()
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
                        )
                    }
                }

                reportsRoute?.let { route ->
                    composable(route) {
                        ReportsScreen(onBack = navController::popBackStack)
                    }
                }
            }
        }
    }
}

internal fun isShiftGateEntry(previousRoute: String?): Boolean =
    previousRoute != Screen.Home.route

internal fun shiftRouteFor(role: UserRole): String = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    UserRole.CASHIER,
    -> Screen.Shift.route
}

internal fun productManagementRouteFor(role: UserRole): String? = when (role) {
    UserRole.OWNER,
    UserRole.ADMIN,
    -> Screen.ProductManagement.route
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

internal const val PRODUCT_CREATED_RESULT_KEY = "product_created"
internal const val PRODUCT_UPDATED_RESULT_KEY = "product_updated"
internal const val STOCK_ADJUSTED_RESULT_KEY = "stock_adjusted"
private const val PRODUCT_ID_ARGUMENT = "productId"

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
    data class Authenticated(val session: NavigationSession) : SessionState
    data object Unauthenticated : SessionState
}

sealed interface NavigationSession {
    val role: UserRole

    data class AuthV2(val value: AuthSession) : NavigationSession {
        override val role: UserRole = value.role
    }
}

internal fun startupRouteFor(state: SessionState): String = when (state) {
    is SessionState.Authenticated -> Screen.Outlet.route
    SessionState.Checking,
    SessionState.Unauthenticated,
    -> AuthV2Screen.Graph.route
}

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val authSessionDataStore: AuthSessionDataStore,
    private val refreshTokenCoordinator: RefreshTokenCoordinator,
    outletRepository: OutletRepository,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Checking)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    val selectedOutlet: StateFlow<Outlet?> = outletRepository.selectedOutlet

    init {
        checkSession()
    }

    fun onAuthV2Authenticated(session: AuthSession) {
        _sessionState.value = SessionState.Authenticated(
            NavigationSession.AuthV2(session),
        )
    }

    fun onLoggedOut() {
        _sessionState.value = SessionState.Unauthenticated
    }

    private fun checkSession() {
        viewModelScope.launch {
            _sessionState.value = resolveStartupSession()
        }
    }

    private suspend fun resolveStartupSession(): SessionState {
        val authV2Session = readAuthV2Session()

        return authV2Session
            ?.let { session -> resolveAuthV2Session(session) }
            ?: SessionState.Unauthenticated
    }

    private suspend fun resolveAuthV2Session(session: AuthSession): SessionState {
        if (session.expiresAt > System.currentTimeMillis()) {
            return SessionState.Authenticated(NavigationSession.AuthV2(session))
        }

        val refreshedAccessToken = try {
            refreshTokenCoordinator.refreshAccessToken(session.accessToken)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

        if (refreshedAccessToken == null) {
            clearAuthV2Session()
            return SessionState.Unauthenticated
        }

        val refreshedSession = readAuthV2Session()

        return if (refreshedSession != null) {
            SessionState.Authenticated(NavigationSession.AuthV2(refreshedSession))
        } else {
            clearAuthV2Session()
            SessionState.Unauthenticated
        }
    }

    private suspend fun readAuthV2Session(): AuthSession? = try {
        authSessionDataStore.getSession()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private suspend fun clearAuthV2Session() {
        try {
            authSessionDataStore.clearSession()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Unit
        }
    }
}
