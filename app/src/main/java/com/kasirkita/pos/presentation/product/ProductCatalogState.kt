package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Stock

data class ProductCatalogItem(
    val product: Product,
    val stockQuantity: Int?,
) {
    val canAddToCart: Boolean
        get() = product.isActive && (!product.trackStock || stockQuantity == null || stockQuantity > 0)
}

data class CategoryFilter(
    val id: String?,
    val label: String,
)

enum class ProductTapAction {
    DirectAdd,
    BlockedUnknownModifiers,
    RequiresModifierSelection,
    BlockedUnavailable,
}

enum class PosLayoutMode { Wide, Narrow }

sealed interface ProductCatalogState {
    data object Loading : ProductCatalogState

    data class Success(
        val items: List<ProductCatalogItem>,
        val categories: List<Category> = emptyList(),
        val message: String? = null,
    ) : ProductCatalogState

    data class Error(val message: String) : ProductCatalogState
}

internal fun filterProductCatalog(
    items: List<ProductCatalogItem>,
    searchQuery: String,
    selectedCategoryId: String?,
): List<ProductCatalogItem> {
    val query = searchQuery.trim()
    return items.filter { item ->
        val matchesSearch = query.isBlank() ||
            item.product.name.contains(query, ignoreCase = true) ||
            item.product.sku.contains(query, ignoreCase = true)
        val matchesCategory = selectedCategoryId == null || item.product.categoryId == selectedCategoryId
        matchesSearch && matchesCategory
    }
}

internal fun buildCategoryFilters(
    categories: List<Category>,
    items: List<ProductCatalogItem>,
): List<CategoryFilter> {
    val availableIds = items.mapNotNull { it.product.categoryId }.toSet()
    return listOf(CategoryFilter(id = null, label = "Semua")) + categories
        .filter { it.id in availableIds }
        .distinctBy(Category::id)
        .map { CategoryFilter(id = it.id, label = it.name) }
}

internal fun productTapAction(product: Product): ProductTapAction = when {
    !product.isActive || (product.trackStock && product.stock == 0) -> ProductTapAction.BlockedUnavailable
    !product.modifierMetadataLoaded -> ProductTapAction.BlockedUnknownModifiers
    product.modifierGroups.isNotEmpty() -> ProductTapAction.RequiresModifierSelection
    else -> ProductTapAction.DirectAdd
}

internal fun posLayoutMode(widthDp: Int): PosLayoutMode =
    if (widthDp >= 840) PosLayoutMode.Wide else PosLayoutMode.Narrow

internal fun mapProductsWithStock(
    products: List<Product>,
    stocks: List<Stock>?,
): List<ProductCatalogItem> {
    val stockByProductId = stocks?.associateBy(Stock::productId)

    return products.map { product ->
        ProductCatalogItem(
            product = product.copy(stock = if (product.trackStock) stockByProductId?.get(product.id)?.quantity else null),
            stockQuantity = if (product.trackStock) {
                stockByProductId?.get(product.id)?.quantity
                    ?: if (stocks == null) null else 0
            } else null,
        )
    }
}
