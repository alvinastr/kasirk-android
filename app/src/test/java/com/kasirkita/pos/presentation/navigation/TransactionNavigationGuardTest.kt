package com.kasirkita.pos.presentation.navigation

import com.kasirkita.pos.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionNavigationGuardTest {

    @Test
    fun ownerAdminAndCashier_canAccessTransactionRoutes() {
        UserRole.entries.forEach { role ->
            assertEquals(
                Screen.Transactions.route,
                transactionHistoryRouteFor(role),
            )
            assertEquals(
                Screen.TransactionDetail.route,
                transactionDetailRouteFor(role),
            )
        }
    }

    @Test
    fun detailRoute_containsSelectedTransactionId() {
        assertEquals(
            "transactions/transaction-id",
            Screen.TransactionDetail.createRoute("transaction-id"),
        )
    }
}
