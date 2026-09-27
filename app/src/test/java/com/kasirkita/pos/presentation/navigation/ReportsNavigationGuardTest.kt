package com.kasirkita.pos.presentation.navigation

import com.kasirkita.pos.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportsNavigationGuardTest {

    @Test
    fun owner_canAccessReportsRoute() {
        assertEquals(Screen.Reports.route, reportsRouteFor(UserRole.OWNER))
    }

    @Test
    fun admin_canAccessReportsRoute() {
        assertEquals(Screen.Reports.route, reportsRouteFor(UserRole.ADMIN))
    }

    @Test
    fun cashier_cannotAccessReportsRoute() {
        assertNull(reportsRouteFor(UserRole.CASHIER))
    }
}
