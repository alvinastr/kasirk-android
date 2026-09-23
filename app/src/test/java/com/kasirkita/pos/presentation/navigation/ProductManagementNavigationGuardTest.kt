package com.kasirkita.pos.presentation.navigation

import com.kasirkita.pos.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductManagementNavigationGuardTest {

    @Test
    fun owner_canAccessProductManagementRoute() {
        assertEquals(
            Screen.ProductManagement.route,
            productManagementRouteFor(UserRole.OWNER),
        )
        assertEquals(
            Screen.ProductCreate.route,
            productCreateRouteFor(UserRole.OWNER),
        )
        assertEquals(
            Screen.ProductEdit.route,
            productEditRouteFor(UserRole.OWNER),
        )
    }

    @Test
    fun admin_canAccessProductManagementRoute() {
        assertEquals(
            Screen.ProductManagement.route,
            productManagementRouteFor(UserRole.ADMIN),
        )
        assertEquals(
            Screen.ProductCreate.route,
            productCreateRouteFor(UserRole.ADMIN),
        )
        assertEquals(
            Screen.ProductEdit.route,
            productEditRouteFor(UserRole.ADMIN),
        )
    }

    @Test
    fun cashier_cannotAccessProductManagementRoute() {
        assertNull(productManagementRouteFor(UserRole.CASHIER))
        assertNull(productCreateRouteFor(UserRole.CASHIER))
        assertNull(productEditRouteFor(UserRole.CASHIER))
    }

    @Test
    fun editRoute_containsSelectedProductId() {
        assertEquals(
            "products/manage/product-id/edit",
            Screen.ProductEdit.createRoute("product-id"),
        )
    }
}
