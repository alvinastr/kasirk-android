package com.kasirkita.pos.presentation.shift

internal sealed interface ShiftCashValidationResult {
    data class Valid(val amount: Long) : ShiftCashValidationResult
    data class Invalid(val message: String) : ShiftCashValidationResult
}

internal fun validateShiftCash(
    input: String,
    label: String,
): ShiftCashValidationResult {
    if (input.isBlank()) {
        return ShiftCashValidationResult.Invalid("$label wajib diisi.")
    }
    val amount = input.toLongOrNull()
        ?: return ShiftCashValidationResult.Invalid("$label harus berupa angka bulat.")
    if (amount < 0L) {
        return ShiftCashValidationResult.Invalid("$label tidak boleh negatif.")
    }
    return ShiftCashValidationResult.Valid(amount)
}
