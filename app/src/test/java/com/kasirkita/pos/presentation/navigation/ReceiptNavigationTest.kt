package com.kasirkita.pos.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReceiptNavigationTest {

    @Test
    fun successfulCheckout_opensReceiptAndClearsSalesFlowToHome() {
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
}
