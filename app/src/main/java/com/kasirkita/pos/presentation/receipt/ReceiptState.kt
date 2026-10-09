package com.kasirkita.pos.presentation.receipt

import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.usecase.PrintReceiptUseCase

sealed interface ReceiptState {
    data object Loading : ReceiptState

    data class Success(
        val receipt: Receipt,
        val document: ReceiptDocument,
        val settings: ReceiptSettings?,
        val paperWidthMm: Int,
        val usedLegacyFallback: Boolean,
        val printContext: PrintReceiptUseCase.ValidatedPrintContext,
    ) : ReceiptState

    data class Error(val message: String) : ReceiptState
}
