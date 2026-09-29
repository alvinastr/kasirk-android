package com.kasirkita.pos.presentation.navigation

sealed class Screen(val route: String) {
    data object Outlet : Screen("outlet")
    data object Shift : Screen("shift")
    data object Home : Screen("home")
    data object Products : Screen("products")
    data object ProductManagement : Screen("products/manage")
    data object ProductCreate : Screen("products/manage/create")
    data object ProductEdit : Screen("products/manage/{productId}/edit") {
        fun createRoute(productId: String): String = "products/manage/$productId/edit"
    }
    data object StockAdjustment : Screen("products/manage/{productId}/stock") {
        fun createRoute(productId: String): String = "products/manage/$productId/stock"
    }
    data object Cart : Screen("cart")
    data object Checkout : Screen("checkout")
    data object Receipt : Screen("receipt/{transactionId}") {
        fun createRoute(transactionId: String): String = "receipt/$transactionId"
    }
    data object Transactions : Screen("transactions")
    data object TransactionDetail : Screen("transactions/{transactionId}") {
        fun createRoute(transactionId: String): String = "transactions/$transactionId"
    }
    data object Reports : Screen("reports")
    data object OfflineRecovery : Screen("offline-recovery")
}
