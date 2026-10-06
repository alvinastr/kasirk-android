package com.kasirkita.pos.presentation.transaction

/**
 * Manual reprint state for a completed transaction.
 * Reprint never opens the cash drawer; it only prints the canonical server receipt.
 */
sealed interface TransactionPrintState {
    data object Idle : TransactionPrintState
    data object Loading : TransactionPrintState
    data object Success : TransactionPrintState
    data class Error(val message: String) : TransactionPrintState
}

internal fun TransactionPrintState.isPrintInFlight(): Boolean = this is TransactionPrintState.Loading