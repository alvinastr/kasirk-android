package com.kasirkita.pos.presentation.navigation

sealed class AuthV2Screen(val route: String) {
    data object Graph : AuthV2Screen("auth/v2")
    data object StoreLogin : AuthV2Screen("auth/v2/store")
    data object UserSelection : AuthV2Screen("auth/v2/users")
    data object PinLogin : AuthV2Screen("auth/v2/pin")
}
