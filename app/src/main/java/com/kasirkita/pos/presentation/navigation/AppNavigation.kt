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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kasirkita.pos.core.datastore.TokenDataStore
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.domain.model.UserSession
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.presentation.auth.LoginScreen
import com.kasirkita.pos.presentation.cart.CartScreen
import com.kasirkita.pos.presentation.checkout.CheckoutScreen
import com.kasirkita.pos.presentation.home.HomeScreen
import com.kasirkita.pos.presentation.outlet.OutletScreen
import com.kasirkita.pos.presentation.product.CreateProductScreen
import com.kasirkita.pos.presentation.product.ProductManagementScreen
import com.kasirkita.pos.presentation.product.ProductScreen
import com.kasirkita.pos.presentation.receipt.ReceiptScreen
import com.kasirkita.pos.presentation.shift.ShiftScreen
import dagger.hilt.android.lifecycle.HiltViewModel
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
            val startDestination = if (authenticatedSession != null) {
                Screen.Outlet.route
            } else {
                Screen.Login.route
            }

            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = { session ->
                            viewModel.onAuthenticated(session)
                            navController.navigate(Screen.Outlet.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
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
                            navController.navigate(Screen.Shift.route)
                        },
                        onManageProductsClick = productManagementRoute?.let { route ->
                            { navController.navigate(route) }
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
                if (manageRoute != null && createRoute != null) {
                    composable(manageRoute) { backStackEntry ->
                        val productCreated by backStackEntry
                            .savedStateHandle
                            .getStateFlow(PRODUCT_CREATED_RESULT_KEY, false)
                            .collectAsState()

                        ProductManagementScreen(
                            onAddProduct = {
                                navController.navigate(createRoute)
                            },
                            productCreated = productCreated,
                            onProductCreatedHandled = {
                                backStackEntry.savedStateHandle[
                                    PRODUCT_CREATED_RESULT_KEY
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
            }
        }
    }
}

internal fun isShiftGateEntry(previousRoute: String?): Boolean =
    previousRoute != Screen.Home.route

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

internal const val PRODUCT_CREATED_RESULT_KEY = "product_created"

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
    data class Authenticated(val session: UserSession) : SessionState
    data object Unauthenticated : SessionState
}

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    outletRepository: OutletRepository,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Checking)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    val selectedOutlet: StateFlow<Outlet?> = outletRepository.selectedOutlet

    init {
        checkSession()
    }

    fun onAuthenticated(session: UserSession) {
        _sessionState.value = SessionState.Authenticated(session)
    }

    private fun checkSession() {
        viewModelScope.launch {
            val session = runCatching {
                tokenDataStore.getSession()
            }.getOrNull()

            _sessionState.value = if (session != null) {
                SessionState.Authenticated(session)
            } else {
                SessionState.Unauthenticated
            }
        }
    }
}
