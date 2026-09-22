package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.repository.ProductRepository
import javax.inject.Inject

class CreateProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {
    suspend operator fun invoke(
        name: String,
        sku: String,
        categoryId: String?,
        price: Long,
        cost: Long,
        minimumStock: Int,
        trackStock: Boolean,
    ): Result<Product> = productRepository.createProduct(
        name = name,
        sku = sku,
        categoryId = categoryId,
        price = price,
        cost = cost,
        minimumStock = minimumStock,
        trackStock = trackStock,
    )
}
