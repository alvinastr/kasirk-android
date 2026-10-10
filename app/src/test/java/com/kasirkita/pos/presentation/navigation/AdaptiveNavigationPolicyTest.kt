package com.kasirkita.pos.presentation.navigation

import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.presentation.more.MoreRouteKey
import com.kasirkita.pos.presentation.more.moreDestinationsFor
import com.kasirkita.pos.presentation.more.moreSectionsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationPolicyTest {

    @Test
    fun cashier_cannotAuthorizeAdministrativeRoutes() {
        listOf(
            Screen.Reports.route,
            Screen.ProductManagement.route,
            Screen.CategoryManagement.route,
            Screen.ModifierGroups.route,
            Screen.PrinterSettings.route,
            Screen.ReceiptTemplateSettings.route,
            "products/manage/product-id/edit",
            "products/manage/product-id/stock",
        ).forEach { route ->
            assertFalse(isRouteAuthorizedForRole(route, UserRole.CASHIER))
        }
        assertFalse(isRouteAuthorizedForRole("shift/unknown", UserRole.CASHIER))
        assertFalse(isRouteAuthorizedForRole("shift/", UserRole.CASHIER))
    }

    @Test
    fun ownerAndAdmin_retainAdministrativeRoutes() {
        listOf(UserRole.OWNER, UserRole.ADMIN).forEach { role ->
            assertTrue(isRouteAuthorizedForRole(Screen.Reports.route, role))
            assertTrue(isRouteAuthorizedForRole(Screen.ProductManagement.route, role))
            assertTrue(isRouteAuthorizedForRole(Screen.PrinterSettings.route, role))
            assertTrue(isRouteAuthorizedForRole(Screen.ReceiptTemplateSettings.route, role))
        }
    }

    @Test
    fun allRoles_canUseOperationalRoutes() {
        UserRole.entries.forEach { role ->
            assertTrue(isRouteAuthorizedForRole(Screen.Home.route, role))
            assertTrue(isRouteAuthorizedForRole(Screen.Products.route, role))
            assertTrue(isRouteAuthorizedForRole(Screen.Transactions.route, role))
            assertTrue(isRouteAuthorizedForRole(Screen.Cart.route, role))
            assertTrue(isRouteAuthorizedForRole(Screen.HeldOrders.route, role))
            assertTrue(
                isRouteAuthorizedForRole(
                    Screen.Shift.createRoute(Screen.Shift.EntryMode.GATE),
                    role,
                ),
            )
        }
    }

    @Test
    fun shiftEntryModes_areExplicitAndRoleAccessible() {
        UserRole.entries.forEach { role ->
            assertTrue(
                isRouteAuthorizedForRole(
                    Screen.Shift.createRoute(Screen.Shift.EntryMode.MANAGE),
                    role,
                ),
            )
        }
    }

    @Test
    fun moreDestinations_areRoleAwareWithoutDisabledTeasers() {
        val cashierKeys = moreDestinationsFor(UserRole.CASHIER).map { it.routeKey }
        assertTrue(cashierKeys.contains(MoreRouteKey.Shift))
        assertTrue(cashierKeys.contains(MoreRouteKey.HeldOrders))
        assertTrue(cashierKeys.contains(MoreRouteKey.OfflineRecovery))
        assertFalse(cashierKeys.contains(MoreRouteKey.ProductManagement))
        assertFalse(cashierKeys.contains(MoreRouteKey.Printer))

        val adminKeys = moreDestinationsFor(UserRole.ADMIN).map { it.routeKey }
        assertTrue(adminKeys.contains(MoreRouteKey.ProductManagement))
        assertTrue(adminKeys.contains(MoreRouteKey.Categories))
        assertTrue(adminKeys.contains(MoreRouteKey.Modifiers))
        assertTrue(adminKeys.contains(MoreRouteKey.Printer))
        assertTrue(adminKeys.contains(MoreRouteKey.ReceiptTemplate))
        assertFalse(adminKeys.contains(MoreRouteKey.HeldOrders))
    }

    @Test
    fun moreShift_usesManageEntryForEveryRole() {
        UserRole.entries.forEach { role ->
            assertTrue(
                moreDestinationRouteFor(
                    destination = MoreRouteKey.Shift,
                    productManagementRoute = null,
                    categoryManagementRoute = null,
                    printerSettingsRoute = null,
                    receiptTemplateSettingsRoute = null,
                    shiftManageRoute = shiftManageRouteFor(role),
                ) == Screen.Shift.createRoute(Screen.Shift.EntryMode.MANAGE),
            )
        }
    }

    @Test
    fun moreSections_areGroupedAndContainNoEmptyGroups() {
        val adminSections = moreSectionsFor(UserRole.ADMIN)
        assertEquals(listOf("Katalog", "Operasional", "Perangkat & Struk"), adminSections.map { it.title })
        assertTrue(adminSections.all { it.destinations.isNotEmpty() })

        val cashierSections = moreSectionsFor(UserRole.CASHIER)
        assertEquals(listOf("Operasional"), cashierSections.map { it.title })
        assertTrue(cashierSections.all { it.destinations.isNotEmpty() })
    }
}
