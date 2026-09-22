package com.kasirkita.pos.presentation.product

internal data class CreateProductFormInput(
    val name: String,
    val sku: String,
    val price: String,
    val cost: String,
    val minimumStock: String,
    val categoryId: String?,
    val trackStock: Boolean,
)

internal data class CreateProductFormErrors(
    val name: String? = null,
    val sku: String? = null,
    val price: String? = null,
    val cost: String? = null,
    val minimumStock: String? = null,
    val categoryId: String? = null,
)

internal data class ValidatedCreateProduct(
    val name: String,
    val sku: String,
    val price: Long,
    val cost: Long,
    val minimumStock: Int,
    val categoryId: String?,
    val trackStock: Boolean,
)

internal sealed interface CreateProductFormResult {
    data class Valid(val product: ValidatedCreateProduct) : CreateProductFormResult
    data class Invalid(val errors: CreateProductFormErrors) : CreateProductFormResult
}

internal fun validateCreateProductForm(
    input: CreateProductFormInput,
): CreateProductFormResult {
    val name = input.name.trim()
    val sku = input.sku.trim()
    val priceText = input.price.trim()
    val costText = input.cost.trim()
    val minimumStockText = input.minimumStock.trim()
    val categoryId = input.categoryId?.trim()

    val price = priceText.toLongOrNull()
    val cost = if (costText.isEmpty()) 0L else costText.toLongOrNull()
    val minimumStock = if (minimumStockText.isEmpty()) {
        0
    } else {
        minimumStockText.toIntOrNull()
    }

    val errors = CreateProductFormErrors(
        name = if (name.isEmpty()) "Nama produk wajib diisi." else null,
        sku = if (sku.isEmpty()) "SKU wajib diisi." else null,
        price = when {
            priceText.isEmpty() -> "Harga jual wajib diisi."
            price == null -> "Harga jual harus berupa angka."
            else -> null
        },
        cost = if (cost == null) "Harga modal harus berupa angka." else null,
        minimumStock = if (minimumStock == null) {
            "Stok minimum harus berupa angka bulat."
        } else {
            null
        },
        categoryId = if (input.categoryId != null && categoryId.isNullOrEmpty()) {
            "Isi ID kategori atau pilih Tanpa kategori."
        } else {
            null
        },
    )

    if (
        errors.name != null ||
        errors.sku != null ||
        errors.price != null ||
        errors.cost != null ||
        errors.minimumStock != null ||
        errors.categoryId != null
    ) {
        return CreateProductFormResult.Invalid(errors)
    }

    return CreateProductFormResult.Valid(
        ValidatedCreateProduct(
            name = name,
            sku = sku,
            price = requireNotNull(price),
            cost = requireNotNull(cost),
            minimumStock = requireNotNull(minimumStock),
            categoryId = categoryId,
            trackStock = input.trackStock,
        ),
    )
}
