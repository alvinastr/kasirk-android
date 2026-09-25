package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class SyncTransactionResult(
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    val status: String,
    val transaction: TransactionDetailResponse?,
    val error: SyncTransactionError?,
)

data class SyncTransactionError(
    @SerializedName("status_code")
    val statusCode: Int,
    @SerializedName("error_code")
    val errorCode: String,
    val message: String,
)
