package com.kasirkita.pos.presentation.stock

import com.kasirkita.pos.domain.model.StockAdjustmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockAdjustmentValidationTest {

    @Test
    fun emptyQuantity_returnsRequiredError() {
        val result = validate(quantity = " ") as StockAdjustmentFormResult.Invalid

        assertEquals("Jumlah wajib diisi.", result.quantityError)
    }

    @Test
    fun nonPositiveQuantity_returnsPositiveNumberError() {
        listOf("0", "-2").forEach { quantity ->
            val result = validate(quantity) as StockAdjustmentFormResult.Invalid

            assertEquals("Jumlah harus lebih dari 0.", result.quantityError)
        }
    }

    @Test
    fun invalidQuantity_returnsIntegerError() {
        val result = validate(quantity = "dua") as StockAdjustmentFormResult.Invalid

        assertEquals("Jumlah harus berupa angka bulat.", result.quantityError)
    }

    @Test
    fun blankReason_mapsToNull() {
        val result = validate(
            quantity = "5",
            reason = "   ",
        ) as StockAdjustmentFormResult.Valid

        assertEquals(5, result.quantity)
        assertNull(result.reason)
    }

    @Test
    fun deductDoesNotPerformClientSideStockValidation() {
        val result = validate(
            quantity = "100",
            adjustmentType = StockAdjustmentType.DEDUCT,
        ) as StockAdjustmentFormResult.Valid

        assertEquals(StockAdjustmentType.DEDUCT, result.adjustmentType)
        assertEquals(100, result.quantity)
    }

    private fun validate(
        quantity: String,
        reason: String = "Penyesuaian manual",
        adjustmentType: StockAdjustmentType = StockAdjustmentType.ADD,
    ): StockAdjustmentFormResult = validateStockAdjustmentForm(
        StockAdjustmentFormInput(
            adjustmentType = adjustmentType,
            quantity = quantity,
            reason = reason,
        ),
    )
}
