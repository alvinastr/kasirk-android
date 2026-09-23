package com.kasirkita.pos.presentation.stock

import com.kasirkita.pos.domain.model.StockAdjustmentType

internal data class StockAdjustmentFormInput(
    val adjustmentType: StockAdjustmentType,
    val quantity: String,
    val reason: String,
)

internal sealed interface StockAdjustmentFormResult {
    data class Valid(
        val adjustmentType: StockAdjustmentType,
        val quantity: Int,
        val reason: String?,
    ) : StockAdjustmentFormResult

    data class Invalid(val quantityError: String) : StockAdjustmentFormResult
}

internal fun validateStockAdjustmentForm(
    input: StockAdjustmentFormInput,
): StockAdjustmentFormResult {
    val quantityText = input.quantity.trim()
    if (quantityText.isEmpty()) {
        return StockAdjustmentFormResult.Invalid("Jumlah wajib diisi.")
    }

    val quantity = quantityText.toIntOrNull()
        ?: return StockAdjustmentFormResult.Invalid("Jumlah harus berupa angka bulat.")

    if (quantity <= 0) {
        return StockAdjustmentFormResult.Invalid("Jumlah harus lebih dari 0.")
    }

    return StockAdjustmentFormResult.Valid(
        adjustmentType = input.adjustmentType,
        quantity = quantity,
        reason = input.reason.trim().takeIf(String::isNotEmpty),
    )
}
