package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.OfflineFinancialSnapshot

data class OfflineTransactionPayload(
    val version: Int,
    val transaction: CreateTransactionRequest,
    @SerializedName("financial_snapshot")
    val financialSnapshot: OfflineFinancialSnapshot,
)

const val LEGACY_PAYLOAD_VERSION = 1
const val CURRENT_PAYLOAD_VERSION = 2

data class OfflineTransactionPayloadV2(
    val version: Int = CURRENT_PAYLOAD_VERSION,
    val transaction: CreateTransactionRequest,
    @SerializedName("financial_snapshot")
    val financialSnapshot: OfflineFinancialSnapshot,
)

internal data class DecodedOfflinePayload(
    val version: Int,
    val transaction: CreateTransactionRequest,
    val financialSnapshot: OfflineFinancialSnapshot,
)
