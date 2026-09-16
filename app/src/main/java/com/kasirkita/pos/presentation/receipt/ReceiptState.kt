package com.kasirkita.pos.presentation.receipt

import com.kasirkita.pos.domain.model.Receipt

sealed interface ReceiptState {
    data object Loading : ReceiptState
    data class Success(val receipt: Receipt) : ReceiptState
    data class Error(val message: String) : ReceiptState
}
