package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class OpenShiftRequest(
    @SerializedName("outlet_id")
    val outletId: String,
)
