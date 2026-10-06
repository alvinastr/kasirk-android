package com.kasirkita.pos.presentation.navigation

import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.presentation.product.PosLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosWorkspaceRailNavigationTest {

    @Test
    fun cashierRail_containsOnlyCashierAllowedDestinations() {
        val destinations = posWorkspaceRailDestinationsFor(UserRole.CASHIER)

        assertEquals(
            listOf(
                Screen.Products.route,
                Screen.Transactions.route,
            ),
            destinations.map { it.route },
        )
        assertFalse(destinations.any { it.route == Screen.ProductManagement.route })
        assertFalse(destinations.any { it.route == Screen.Reports.route })
    }

    @Test
    fun ownerRail_containsExistingOwnerDestinations() {
        val destinations = posWorkspaceRailDestinationsFor(UserRole.OWNER)

        assertEquals(
            listOf(
                Screen.Products.route,
                Screen.Transactions.route,
                Screen.ProductManagement.route,
                Screen.Reports.route,
                Screen.PrinterSettings.route,
            ),
            destinations.map { it.route },
        )
    }

    @Test
    fun adminRail_containsExistingAdminDestinations() {
        val destinations = posWorkspaceRailDestinationsFor(UserRole.ADMIN)

        assertEquals(
            listOf(
                Screen.Products.route,
                Screen.Transactions.route,
                Screen.ProductManagement.route,
                Screen.Reports.route,
                Screen.PrinterSettings.route,
            ),
            destinations.map { it.route },
        )
    }

    @Test
    fun posDestination_isSelectedInWorkspace() {
        val destinations = posWorkspaceRailDestinationsFor(UserRole.CASHIER)

        assertEquals(Screen.Products.route, destinations.single { it.selected }.route)
        assertEquals("POS", destinations.single { it.selected }.label)
    }

    @Test
    fun wideModeExposesRailAndNarrowModeDoesNot() {
        assertTrue(posWorkspaceShowsRail(PosLayoutMode.Wide))
        assertFalse(posWorkspaceShowsRail(PosLayoutMode.Narrow))
    }
}
