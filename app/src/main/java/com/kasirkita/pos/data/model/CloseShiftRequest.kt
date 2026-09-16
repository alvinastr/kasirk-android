package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class CloseShiftRequest(
    @SerializedName("closing_cash")
    val closingCash: Long,
)
