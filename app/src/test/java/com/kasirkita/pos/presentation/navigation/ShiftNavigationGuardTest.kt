package com.kasirkita.pos.presentation.navigation

import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.presentation.shift.shouldNavigateToHomeFromShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftNavigationGuardTest {

    private fun outlet(
        id: String = "outlet-id",
        tenantId: String = "tenant-id",
        name: String = "Test Outlet",
    ) = Outlet(
        id = id,
        tenantId = tenantId,
        name = name,
        address = null,
        isActive = true,
        createdAt = "2026-09-27T00:00:00Z",
    )

    @Test
    fun shiftWithoutOutlet_shouldRecoverToOutlet() {
        assertTrue(shouldRecoverShiftToOutlet(selectedOutlet = null))
    }

    @Test
    fun shiftWithOutlet_shouldNotRecover() {
        assertFalse(
            shouldRecoverShiftToOutlet(
                selectedOutlet = outlet(id = "outlet-1", name = "Test Outlet"),
            ),
        )
    }

    @Test
    fun everyAuthenticatedRole_canAccessShiftRoute() {
        UserRole.entries.forEach { role ->
            assertEquals(Screen.Shift.route, shiftRouteFor(role))
        }
    }

    @Test
    fun initialShiftGate_withMatchingOpenShift_navigatesToHome() {
        assertTrue(
            shouldNavigateToHomeFromShift(
                autoNavigateToHome = isShiftGateEntry(previousRoute = null),
                selectedOutletId = OUTLET_ID,
                shift = shift(status = "OPEN"),
            ),
        )
    }

    @Test
    fun shiftOpenedFromHome_doesNotRedirectBackToHome() {
        assertFalse(
            shouldNavigateToHomeFromShift(
                autoNavigateToHome = isShiftGateEntry(previousRoute = Screen.Home.route),
                selectedOutletId = OUTLET_ID,
                shift = shift(status = "OPEN"),
            ),
        )
    }

    @Test
    fun openShiftForDifferentOutlet_doesNotPassGate() {
        assertFalse(
            shouldNavigateToHomeFromShift(
                autoNavigateToHome = true,
                selectedOutletId = "another-outlet",
                shift = shift(status = "OPEN"),
            ),
        )
    }

    @Test
    fun nonOpenShift_doesNotPassGate() {
        assertFalse(
            shouldNavigateToHomeFromShift(
                autoNavigateToHome = true,
                selectedOutletId = OUTLET_ID,
                shift = shift(status = "CLOSED"),
            ),
        )
    }

    private fun shift(status: String) = Shift(
        id = "shift-id",
        outletId = OUTLET_ID,
        userId = "user-id",
        openingCash = 50_000L,
        closingCash = null,
        expectedCash = null,
        difference = null,
        status = status,
        openedAt = "2026-09-17T00:00:00.000Z",
        closedAt = null,
    )

    private companion object {
        const val OUTLET_ID = "outlet-id"
    }
}
