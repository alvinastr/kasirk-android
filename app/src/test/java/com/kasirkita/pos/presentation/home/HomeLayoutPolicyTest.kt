package com.kasirkita.pos.presentation.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutPolicyTest {

    @Test
    fun homeWideLayout_usesEstablishedBoundary() {
        assertFalse(homeUsesWideLayout(839))
        assertTrue(homeUsesWideLayout(840))
        assertTrue(homeUsesWideLayout(841))
    }
}
