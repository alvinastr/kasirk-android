package com.kasirkita.pos.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kasirkita.pos.domain.model.AuthSession
import com.kasirkita.pos.presentation.authv2.AuthV2State
import com.kasirkita.pos.presentation.authv2.AuthV2ViewModel
import com.kasirkita.pos.presentation.authv2.PinLoginScreen
import com.kasirkita.pos.presentation.authv2.StoreLoginScreen
import com.kasirkita.pos.presentation.authv2.UserSelectionScreen

@Composable
fun AuthV2Navigation(
    onAuthenticated: (AuthSession) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthV2ViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    LaunchedEffect(state, currentRoute) {
        when (val currentState = state) {
            is AuthV2State.UserSelection -> {
                if (currentRoute == AuthV2Screen.StoreLogin.route) {
                    navController.navigate(AuthV2Screen.UserSelection.route) {
                        launchSingleTop = true
                    }
                }
            }
            is AuthV2State.PinLogin -> {
                if (currentRoute == AuthV2Screen.UserSelection.route) {
                    navController.navigate(AuthV2Screen.PinLogin.route) {
                        launchSingleTop = true
                    }
                }
            }
            is AuthV2State.Authenticated -> onAuthenticated(currentState.session)
            is AuthV2State.StoreLogin -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthV2Screen.StoreLogin.route,
        route = AuthV2Screen.Graph.route,
        modifier = modifier,
    ) {
        composable(AuthV2Screen.StoreLogin.route) {
            StoreLoginScreen(
                state = state as? AuthV2State.StoreLogin
                    ?: AuthV2State.StoreLogin(isLoading = true),
                onResolveStore = viewModel::resolveStore,
            )
        }
        composable(AuthV2Screen.UserSelection.route) {
            val currentState = state as? AuthV2State.UserSelection
            if (currentState == null) {
                AuthV2TransitionContent()
            } else {
                UserSelectionScreen(
                    store = currentState.store,
                    onUserSelected = viewModel::selectUser,
                    onBack = {
                        viewModel.returnToStoreLogin()
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(AuthV2Screen.PinLogin.route) {
            val currentState = state as? AuthV2State.PinLogin
            if (currentState == null) {
                AuthV2TransitionContent()
            } else {
                PinLoginScreen(
                    state = currentState,
                    onLogin = viewModel::loginWithPin,
                    onBack = {
                        viewModel.returnToUserSelection()
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthV2TransitionContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("Menyiapkan login")
        }
    }
}
