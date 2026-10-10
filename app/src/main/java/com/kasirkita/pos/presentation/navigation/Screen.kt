package com.kasirkita.pos.presentation.navigation

sealed class Screen(val route: String) {
    data object Outlet : Screen("outlet")
    data object Shift : Screen("shift/{entryMode}") {
        enum class EntryMode(val routeValue: String) {
            GATE("gate"),
            MANAGE("manage"),
        }

        fun createRoute(entryMode: EntryMode): String =
            "shift/${entryMode.routeValue}"

        fun parseEntryMode(value: String?): EntryMode? =
            EntryMode.entries.firstOrNull { it.routeValue == value }

        fun parseRoute(route: String?): EntryMode? {
            val value = route?.removePrefix("shift/") ?: return null
            if (value == route || value.contains('/')) return null
            return parseEntryMode(value)
        }
    }
    data object Home : Screen("home")
    data object More : Screen("more")
    data object Products : Screen("products")
    data object ProductManagement : Screen("products/manage")
    data object ProductCreate : Screen("products/manage/create")
    data object ProductEdit : Screen("products/manage/{productId}/edit") {
        fun createRoute(productId: String): String = "products/manage/$productId/edit"
    }
    data object StockAdjustment : Screen("products/manage/{productId}/stock") {
        fun createRoute(productId: String): String = "products/manage/$productId/stock"
    }
    data object CategoryManagement : Screen("categories")
    data object ModifierGroups : Screen("modifiers")
    data object ModifierGroupDetail : Screen("modifiers/{groupId}") {
        fun createRoute(groupId: String): String = "modifiers/$groupId"
    }
    data object Cart : Screen("cart")
    data object HeldOrders : Screen("held-orders")
    data object Checkout : Screen("checkout")
    data object Receipt : Screen("receipt/{transactionId}") {
        fun createRoute(transactionId: String): String = "receipt/$transactionId"
    }
    data object Transactions : Screen("transactions")
    data object TransactionDetail : Screen("transactions/{transactionId}") {
        fun createRoute(transactionId: String): String = "transactions/$transactionId"
    }
    data object Reports : Screen("reports")
    data object PrinterSettings : Screen("settings/printer")
    data object ReceiptTemplateSettings : Screen("settings/receipt-template")
    data object OfflineRecovery : Screen("offline-recovery")
}
