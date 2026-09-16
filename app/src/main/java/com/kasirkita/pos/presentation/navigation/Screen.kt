package com.kasirkita.pos.presentation.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Outlet : Screen("outlet")
    data object Shift : Screen("shift")
    data object Home : Screen("home")
    data object Products : Screen("products")
    data object Cart : Screen("cart")
    data object Checkout : Screen("checkout")
}
