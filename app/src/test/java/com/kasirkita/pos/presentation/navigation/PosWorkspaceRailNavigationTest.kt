package com.kasirkita.pos.presentation.navigation

import com.kasirkita.pos.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosWorkspaceRailNavigationTest {

    @Test
    fun ownerAndAdmin_haveFiveTopLevelDestinations() {
        val expected = listOf(
            Screen.Home.route,
            Screen.Products.route,
            Screen.Transactions.route,
            Screen.Reports.route,
            Screen.More.route,
        )

        assertEquals(expected, topLevelDestinationsFor(UserRole.OWNER).map { it.route })
        assertEquals(expected, topLevelDestinationsFor(UserRole.ADMIN).map { it.route })
    }

    @Test
    fun cashier_hasNoReportsDestination() {
        val destinations = topLevelDestinationsFor(UserRole.CASHIER)

        assertEquals(
            listOf(
                Screen.Home.route,
                Screen.Products.route,
                Screen.Transactions.route,
                Screen.More.route,
            ),
            destinations.map { it.route },
        )
        assertFalse(destinations.any { it.route == Screen.Reports.route })
    }

    @Test
    fun topLevelRoutes_mapFocusedWorkflowsToTheirOwningDestination() {
        assertEquals(Screen.Products.route, topLevelRouteFor(Screen.Cart.route))
        assertEquals(Screen.Products.route, topLevelRouteFor(Screen.Checkout.route))
        assertEquals(Screen.Transactions.route, topLevelRouteFor(Screen.TransactionDetail.route))
        assertEquals(Screen.More.route, topLevelRouteFor(Screen.PrinterSettings.route))
        assertEquals(Screen.More.route, topLevelRouteFor(Screen.OfflineRecovery.route))
    }

    @Test
    fun persistentNavigation_isHiddenForFocusedFlows() {
        assertTrue(shouldShowPersistentNavigation(Screen.Home.route))
        assertTrue(shouldShowPersistentNavigation(Screen.Products.route))
        assertFalse(shouldShowPersistentNavigation(Screen.Cart.route))
        assertFalse(shouldShowPersistentNavigation(Screen.Checkout.route))
        assertFalse(shouldShowPersistentNavigation(Screen.Receipt.route))
        assertFalse(shouldShowPersistentNavigation(Screen.ProductCreate.route))
        assertFalse(shouldShowPersistentNavigation(Screen.PrinterSettings.route))
    }

    @Test
    fun adaptiveChrome_usesBottomBarOnPhoneAndRailOnTablet() {
        assertEquals(
            NavigationChrome.BottomBar,
            navigationChromeFor(UserRole.CASHIER, Screen.Products.route, 411),
        )
        assertEquals(
            NavigationChrome.Rail,
            navigationChromeFor(UserRole.CASHIER, Screen.Products.route, 840),
        )
        assertEquals(
            NavigationChrome.Hidden,
            navigationChromeFor(UserRole.CASHIER, Screen.Checkout.route, 1024),
        )
    }

    @Test
    fun widePos_usesTheGlobalScaffoldBoundary() {
        assertFalse(usesTabletNavigation(839))
        assertTrue(usesTabletNavigation(840))
        assertTrue(usesTabletNavigation(841))
        assertEquals(Screen.Products.route, topLevelRouteFor(Screen.Products.route))
        assertEquals("Beranda", topLevelDestinationsFor(UserRole.CASHIER).first().label)
    }

    @Test
    fun nullRole_hasNoNavigationChrome() {
        assertEquals(
            NavigationChrome.Hidden,
            navigationChromeFor(null, Screen.Home.route, 1024),
        )
    }
}
