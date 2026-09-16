package com.kasirkita.pos.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kasirkita.pos.core.datastore.TokenDataStore
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.presentation.auth.LoginScreen
import com.kasirkita.pos.presentation.cart.CartScreen
import com.kasirkita.pos.presentation.checkout.CheckoutScreen
import com.kasirkita.pos.presentation.home.HomeScreen
import com.kasirkita.pos.presentation.outlet.OutletScreen
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
    viewModel: AppNavigationViewModel = viewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val selectedOutlet by viewModel.selectedOutlet.collectAsState()

    when (val currentState = sessionState) {
        SessionState.Checking -> SessionLoadingContent()
        SessionState.Authenticated,
        SessionState.Unauthenticated,
        -> {
            val navController = rememberNavController()
            val startDestination = if (currentState == SessionState.Authenticated) {
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
                        onLoginSuccess = {
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

                composable(Screen.Shift.route) {
                    val outlet = selectedOutlet
                    if (outlet == null) {
                        SessionLoadingContent()
                    } else {
                        ShiftScreen(
                            outletId = outlet.id,
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
                    )
                }

                composable(Screen.Products.route) {
                    ProductScreen()
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
    data object Authenticated : SessionState
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

    private fun checkSession() {
        viewModelScope.launch {
            val hasToken = runCatching {
                !tokenDataStore.getToken().isNullOrBlank()
            }.getOrDefault(false)

            _sessionState.value = if (hasToken) {
                SessionState.Authenticated
            } else {
                SessionState.Unauthenticated
            }
        }
    }
}
