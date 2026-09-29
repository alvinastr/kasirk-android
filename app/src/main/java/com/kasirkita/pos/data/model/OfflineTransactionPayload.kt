package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot

data class OfflineTransactionPayload(
    val version: Int,
    val transaction: CreateTransactionRequest,
    @SerializedName("financial_snapshot")
    val financialSnapshot: OfflineFinancialSnapshot,
)
