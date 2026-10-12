package com.kasirkita.pos.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldOrdersNavigationTest {

    @Test
    fun cashierHeldOrdersAction_opensHeldOrdersSingleTop() {
        var destination: HeldOrdersNavigationDestination? = null
        val action = heldOrdersNavigationAction { destination = it }

        action()

        assertEquals(Screen.HeldOrders.route, destination?.route)
        assertTrue(destination?.launchSingleTop == true)
    }

    @Test
    fun resumedHeldOrder_addProducts_continuesToProductsAndPreservesBackStack() {
        val destination = heldOrderContinuationDestination(HeldOrderContinuationAction.ADD_PRODUCTS)

        assertEquals(Screen.Products.route, destination.route)
        assertTrue(destination.launchSingleTop)
        assertEquals(null, destination.popUpToRoute)
    }

    @Test
    fun resumedHeldOrder_processPayment_continuesToCheckoutAndPreservesBackStack() {
        val destination = heldOrderContinuationDestination(HeldOrderContinuationAction.PROCESS_PAYMENT)

        assertEquals(Screen.Checkout.route, destination.route)
        assertTrue(destination.launchSingleTop)
        assertEquals(null, destination.popUpToRoute)
    }

    @Test
    fun heldOrderUpdateSuccess_replacesSalesStackWithHeldOrders() {
        val plan = topLevelNavigationPlan(Screen.HeldOrders.route, homePresent = true)

        assertEquals(Screen.HeldOrders.route, plan.operations.single().route)
        assertEquals(TopLevelPopUpTarget.HOME, plan.operations.single().popUpTo)
    }
}
