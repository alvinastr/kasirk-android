package com.kasirkita.pos.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldOrdersNavigationTest {

    @Test
    fun embeddedPosHeldOrdersAction_opensHeldOrdersSingleTop() {
        var destination: HeldOrdersNavigationDestination? = null
        val action = heldOrdersNavigationAction { destination = it }

        action()

        assertEquals(Screen.HeldOrders.route, destination?.route)
        assertTrue(destination?.launchSingleTop == true)
    }
}
