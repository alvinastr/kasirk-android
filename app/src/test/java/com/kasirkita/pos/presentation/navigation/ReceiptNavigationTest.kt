package com.kasirkita.pos.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptNavigationTest {

    @Test
    fun successfulCheckout_opensReceiptAndClearsSalesFlowToHomeWhenPresent() {
        val destination = receiptDestination("transaction-id")

        assertEquals("receipt/transaction-id", destination.route)
        assertEquals(Screen.Home.route, destination.popUpToRoute)
        assertFalse(destination.popUpToInclusive)
    }

    @Test
    fun receiptSuccessAction_startsNewTransactionFromProducts() {
        val destination = newTransactionDestination()

        assertEquals(Screen.Products.route, destination.route)
        assertEquals(Screen.Home.route, destination.popUpToRoute)
        assertFalse(destination.popUpToInclusive)
    }

    @Test
    fun salesFlow_fallsBackToProductsWhenHomeIsNotOnTheStack() {
        assertEquals(Screen.Home.route, salesFlowPopUpRoute(hasPreferredRoute = true))
        assertEquals(Screen.Products.route, salesFlowPopUpRoute(hasPreferredRoute = false))
    }

    @Test
    fun topLevelNavigation_homeAbsentFirstEstablishesHomeAnchor() {
        val plan = topLevelNavigationPlan(Screen.Transactions.route, homePresent = false)

        assertEquals(
            listOf(Screen.Home.route, Screen.Transactions.route),
            plan.operations.map { it.route },
        )
        assertEquals(TopLevelPopUpTarget.AUTHENTICATED_GRAPH, plan.operations[0].popUpTo)
        assertEquals(TopLevelPopUpTarget.HOME, plan.operations[1].popUpTo)
    }

    @Test
    fun topLevelNavigation_homePresentKeepsExistingAnchor() {
        val plan = topLevelNavigationPlan(Screen.More.route, homePresent = true)

        assertEquals(listOf(Screen.More.route), plan.operations.map { it.route })
        assertEquals(TopLevelPopUpTarget.HOME, plan.operations.single().popUpTo)
        assertTrue(plan.operations.single().saveState)
        assertTrue(plan.operations.single().restoreState)
    }

    @Test
    fun topLevelNavigation_homeAbsentSelectingHomeCreatesOnlyHome() {
        val plan = topLevelNavigationPlan(Screen.Home.route, homePresent = false)

        assertEquals(listOf(Screen.Home.route), plan.operations.map { it.route })
        assertEquals(
            TopLevelPopUpTarget.AUTHENTICATED_GRAPH,
            plan.operations.single().popUpTo,
        )
    }

    @Test
    fun topLevelNavigation_homeAbsentSelectingMoreCreatesHomeThenMore() {
        val plan = topLevelNavigationPlan(Screen.More.route, homePresent = false)

        assertEquals(
            listOf(Screen.Home.route, Screen.More.route),
            plan.operations.map { it.route },
        )
        assertEquals(TopLevelPopUpTarget.AUTHENTICATED_GRAPH, plan.operations[0].popUpTo)
        assertEquals(TopLevelPopUpTarget.HOME, plan.operations[1].popUpTo)
    }

    @Test
    fun topLevelNavigation_firstShiftSuccessCreatesHomeThenKasir() {
        val plan = topLevelNavigationPlan(Screen.Products.route, homePresent = false)

        assertEquals(
            listOf(Screen.Home.route, Screen.Products.route),
            plan.operations.map { it.route },
        )
        assertEquals(TopLevelPopUpTarget.HOME, plan.operations[1].popUpTo)
    }

    @Test
    fun shiftEntryModeParser_isStrict() {
        assertEquals(
            Screen.Shift.EntryMode.GATE,
            Screen.Shift.parseEntryMode("gate"),
        )
        assertEquals(
            Screen.Shift.EntryMode.MANAGE,
            Screen.Shift.parseEntryMode("manage"),
        )
        listOf(null, "", " ", "GATE", "unknown", "gate/").forEach { value ->
            assertEquals(null, Screen.Shift.parseEntryMode(value))
        }
        assertEquals(null, Screen.Shift.parseRoute("shift/unknown"))
        assertEquals(null, Screen.Shift.parseRoute("shift/"))
        assertEquals(null, Screen.Shift.parseRoute("shift/GATE"))
    }

    @Test
    fun malformedShiftRecovery_neverTreatsGateOrOutletAsAuthenticatedBackTarget() {
        assertTrue(isSafeAuthenticatedBackTarget(Screen.Home.route))
        assertTrue(isSafeAuthenticatedBackTarget(Screen.More.route))
        assertFalse(isSafeAuthenticatedBackTarget(Screen.Outlet.route))
        assertFalse(
            isSafeAuthenticatedBackTarget(
                Screen.Shift.createRoute(Screen.Shift.EntryMode.GATE),
            ),
        )
        assertFalse(isSafeAuthenticatedBackTarget(AuthV2Screen.Graph.route))
    }
}
