package com.kasirkita.pos.presentation.transaction

import com.kasirkita.pos.domain.model.Transaction

sealed interface TransactionHistoryState {
    data object Loading : TransactionHistoryState
    data object Empty : TransactionHistoryState
    data class Success(val transactions: List<Transaction>) : TransactionHistoryState
    data class Error(val message: String) : TransactionHistoryState
}

sealed interface TransactionDetailState {
    data object Loading : TransactionDetailState
    data class Success(val transaction: Transaction) : TransactionDetailState
    data class Error(val message: String) : TransactionDetailState
}
