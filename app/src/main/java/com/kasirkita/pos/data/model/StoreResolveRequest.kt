package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName

data class StoreResolveRequest(
    @SerializedName("store_code")
    val storeCode: String,
)
