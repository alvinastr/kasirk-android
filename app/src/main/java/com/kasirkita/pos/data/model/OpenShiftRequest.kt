package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class OpenShiftRequest(
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("opening_cash")
    val openingCash: Long,
)
