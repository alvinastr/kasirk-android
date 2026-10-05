package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogStateTest {

    @Test
    fun trackedProduct_mapsOutletStockQuantity() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = true)),
            stocks = listOf(stock(quantity = 7)),
        ).single()

        assertEquals(7, item.stockQuantity)
        assertTrue(item.canAddToCart)
    }

    @Test
    fun trackedProductWithZeroStock_cannotBeAdded() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = true)),
            stocks = listOf(stock(quantity = 0)),
        ).single()

        assertEquals(0, item.stockQuantity)
        assertFalse(item.canAddToCart)
    }

    @Test
    fun trackedProductWithoutStockRow_isTreatedAsZeroStock() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = true)),
            stocks = emptyList(),
        ).single()

        assertEquals(0, item.stockQuantity)
        assertFalse(item.canAddToCart)
    }

    @Test
    fun trackedProduct_whenStockIsUnavailable_canBeAddedOffline() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = true)),
            stocks = null,
        ).single()

        assertNull(item.stockQuantity)
        assertTrue(item.canAddToCart)
    }

    @Test
    fun untrackedProduct_doesNotExposeQuantityAndCanBeAdded() {
        val item = mapProductsWithStock(
            products = listOf(product(trackStock = false)),
            stocks = listOf(stock(quantity = 0)),
        ).single()

        assertNull(item.stockQuantity)
        assertTrue(item.canAddToCart)
    }

    @Test
    fun filterCatalog_matchesProductName() {
        val results = filterProductCatalog(
            items = catalogItems(),
            searchQuery = "susu",
            selectedCategoryId = null,
        )

        assertEquals(listOf("kopi"), results.map { it.product.id })
    }

    @Test
    fun filterCatalog_matchesSku() {
        val results = filterProductCatalog(
            items = catalogItems(),
            searchQuery = "SKU-TEH",
            selectedCategoryId = null,
        )

        assertEquals(listOf("teh"), results.map { it.product.id })
    }

    @Test
    fun filterCatalog_isCaseInsensitiveAndTrimsQuery() {
        val results = filterProductCatalog(
            items = catalogItems(),
            searchQuery = "  KOPI  ",
            selectedCategoryId = null,
        )

        assertEquals(listOf("kopi"), results.map { it.product.id })
    }

    @Test
    fun filterCatalog_returnsEmptyForNoMatch() {
        val results = filterProductCatalog(
            items = catalogItems(),
            searchQuery = "burger",
            selectedCategoryId = null,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun filterCatalog_allCategoryReturnsAllEligibleProducts() {
        val results = filterProductCatalog(
            items = catalogItems(),
            searchQuery = "",
            selectedCategoryId = null,
        )

        assertEquals(listOf("kopi", "teh", "snack"), results.map { it.product.id })
    }

    @Test
    fun filterCatalog_filtersCategoryByStableId() {
        val results = filterProductCatalog(
            items = catalogItems(),
            searchQuery = "",
            selectedCategoryId = "cat-drink",
        )

        assertEquals(listOf("kopi", "teh"), results.map { it.product.id })
    }

    @Test
    fun filterCatalog_combinesSearchAndCategory() {
        val results = filterProductCatalog(
            items = catalogItems(),
            searchQuery = "teh",
            selectedCategoryId = "cat-drink",
        )

        assertEquals(listOf("teh"), results.map { it.product.id })
    }

    @Test
    fun categoryFilters_useHumanReadableNamesNotRawIds() {
        val filters = buildCategoryFilters(
            categories = listOf(category("cat-drink", "Minuman")),
            items = catalogItems(),
        )

        assertEquals("Semua", filters.first().label)
        assertEquals("Minuman", filters[1].label)
        assertFalse(filters.any { it.label == "cat-drink" })
    }

    @Test
    fun modifierSafety_unknownCannotDirectAdd() {
        val action = productTapAction(product(modifierMetadataLoaded = false))

        assertEquals(ProductTapAction.BlockedUnknownModifiers, action)
    }

    @Test
    fun modifierSafety_knownNoneCanDirectAdd() {
        val action = productTapAction(product(modifierMetadataLoaded = true))

        assertEquals(ProductTapAction.DirectAdd, action)
    }

    @Test
    fun modifierSafety_requiredConfigurationCannotDirectAdd() {
        val action = productTapAction(
            product(
                modifierMetadataLoaded = true,
                modifierGroups = listOf(modifierGroup(required = true)),
            ),
        )

        assertEquals(ProductTapAction.RequiresModifierSelection, action)
    }

    @Test
    fun modifierSafety_optionalConfigurationDoesNotFabricateDefaultSelection() {
        val action = productTapAction(
            product(
                modifierMetadataLoaded = true,
                modifierGroups = listOf(modifierGroup(required = false)),
            ),
        )

        assertEquals(ProductTapAction.RequiresModifierSelection, action)
    }

    @Test
    fun layoutMode_wideBreakpointSelectsSideBySide() {
        assertEquals(PosLayoutMode.Wide, posLayoutMode(widthDp = 900))
    }

    @Test
    fun layoutMode_narrowBreakpointSelectsFallback() {
        assertEquals(PosLayoutMode.Narrow, posLayoutMode(widthDp = 599))
    }

    @Test
    fun layoutMode_boundaryIsDeterministic() {
        assertEquals(PosLayoutMode.Wide, posLayoutMode(widthDp = 840))
        assertEquals(PosLayoutMode.Narrow, posLayoutMode(widthDp = 839))
    }

    private fun catalogItems(): List<ProductCatalogItem> = listOf(
        ProductCatalogItem(product(id = "kopi", categoryId = "cat-drink", name = "Kopi Susu", sku = "SKU-KOPI"), 10),
        ProductCatalogItem(product(id = "teh", categoryId = "cat-drink", name = "Teh Tawar", sku = "SKU-TEH"), 10),
        ProductCatalogItem(product(id = "snack", categoryId = "cat-snack", name = "Keripik", sku = "SKU-SNACK"), null),
    )

    private fun product(
        id: String = "product-id",
        categoryId: String? = null,
        name: String = "Kopi Susu",
        sku: String = "KOPI-SUSU",
        trackStock: Boolean = true,
        modifierMetadataLoaded: Boolean = true,
        modifierGroups: List<ModifierGroup> = emptyList(),
    ) = Product(
        id = id,
        tenantId = "tenant-id",
        categoryId = categoryId,
        name = name,
        sku = sku,
        price = 20_000L,
        cost = 10_000L,
        minimumStock = 5,
        trackStock = trackStock,
        isActive = true,
        createdAt = "2026-09-17T00:00:00.000Z",
        modifierGroups = modifierGroups,
        modifierMetadataLoaded = modifierMetadataLoaded,
    )

    private fun stock(quantity: Int) = Stock(
        id = "stock-id",
        outletId = "outlet-id",
        productId = "product-id",
        quantity = quantity,
        updatedAt = "2026-09-28T00:00:00.000Z",
    )

    private fun category(id: String, name: String) = Category(
        id = id,
        tenantId = "tenant-id",
        name = name,
        createdAt = "2026-09-28T00:00:00.000Z",
    )

    private fun modifierGroup(required: Boolean) = ModifierGroup(
        id = "modifier-group-id-$required",
        tenantId = "tenant-id",
        name = "Pilihan",
        isActive = true,
        displayOrder = 1,
        required = required,
    )
}
