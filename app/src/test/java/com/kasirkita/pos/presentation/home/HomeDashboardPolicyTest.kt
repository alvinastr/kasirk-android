package com.kasirkita.pos.presentation.home

import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.presentation.cart.HeldOrderTotalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardPolicyTest {
    @Test
    fun ownerAndAdmin_canLoadDailySales() {
        assertTrue(homeLoadsDailySales(UserRole.OWNER))
        assertTrue(homeLoadsDailySales(UserRole.ADMIN))
    }

    @Test
    fun cashierAndUnknown_cannotLoadDailySales() {
        assertFalse(homeLoadsDailySales(UserRole.CASHIER))
        assertFalse(homeLoadsDailySales(null))
    }

    @Test
    fun heldOrderPresentation_distinguishesFreshStaleLoadingErrorAndNotLoaded() {
        assertEquals(HeldOrderHomePresentation.AVAILABLE(0), heldOrderHomePresentation(HeldOrderTotalState.Available(0)))
        assertEquals(HeldOrderHomePresentation.AVAILABLE(3), heldOrderHomePresentation(HeldOrderTotalState.Available(3)))
        assertEquals(HeldOrderHomePresentation.LOADING, heldOrderHomePresentation(HeldOrderTotalState.Loading()))
        assertEquals(HeldOrderHomePresentation.STALE(3), heldOrderHomePresentation(HeldOrderTotalState.Stale(3, "offline")))
        assertTrue(heldOrderHomePresentation(HeldOrderTotalState.Error("offline")) is HeldOrderHomePresentation.ERROR)
        assertEquals(HeldOrderHomePresentation.NOT_LOADED, heldOrderHomePresentation(HeldOrderTotalState.NotLoaded))
    }
}
