package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.Stock

data class ProductCatalogItem(
    val product: Product,
    val stockQuantity: Int?,
) {
    val canAddToCart: Boolean
        get() = !product.trackStock || stockQuantity == null || stockQuantity > 0
}

sealed interface ProductCatalogState {
    data object Loading : ProductCatalogState

    data class Success(
        val items: List<ProductCatalogItem>,
        val message: String? = null,
    ) : ProductCatalogState

    data class Error(val message: String) : ProductCatalogState
}

internal fun mapProductsWithStock(
    products: List<Product>,
    stocks: List<Stock>?,
): List<ProductCatalogItem> {
    val stockByProductId = stocks?.associateBy(Stock::productId)

    return products.map { product ->
        ProductCatalogItem(
            product = product,
            stockQuantity = if (product.trackStock) {
                stockByProductId?.get(product.id)?.quantity
                    ?: if (stocks == null) null else 0
            } else {
                null
            },
        )
    }
}
