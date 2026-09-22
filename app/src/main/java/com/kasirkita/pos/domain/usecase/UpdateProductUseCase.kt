package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.ProductRepository
import javax.inject.Inject

class UpdateProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {
    suspend operator fun invoke(
        productId: String,
        name: String? = null,
        sku: String? = null,
        categoryId: String? = null,
        price: Long? = null,
        cost: Long? = null,
        minimumStock: Int? = null,
        trackStock: Boolean? = null,
    ): Result<Product> = productRepository.updateProduct(
        productId = productId,
        name = name,
        sku = sku,
        categoryId = categoryId,
        price = price,
        cost = cost,
        minimumStock = minimumStock,
        trackStock = trackStock,
    )
}
