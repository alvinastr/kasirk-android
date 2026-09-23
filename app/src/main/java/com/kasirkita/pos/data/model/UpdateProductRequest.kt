package com.kasirkita.pos.data.model

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class UpdateProductRequest(
    val name: String? = null,
    val sku: String? = null,
    @SerializedName("category_id")
    val categoryId: String? = null,
    val price: Long? = null,
    val cost: Long? = null,
    @SerializedName("minimum_stock")
    val minimumStock: Int? = null,
    @SerializedName("track_stock")
    val trackStock: Boolean? = null,
    @Transient
    val categoryIdChanged: Boolean = false,
)

fun UpdateProductRequest.toJsonObject(): JsonObject = JsonObject().apply {
    name?.let { addProperty("name", it) }
    sku?.let { addProperty("sku", it) }
    price?.let { addProperty("price", it) }
    cost?.let { addProperty("cost", it) }
    minimumStock?.let { addProperty("minimum_stock", it) }
    trackStock?.let { addProperty("track_stock", it) }

    if (categoryIdChanged) {
        if (categoryId == null) {
            add("category_id", JsonNull.INSTANCE)
        } else {
            addProperty("category_id", categoryId)
        }
    }
}
