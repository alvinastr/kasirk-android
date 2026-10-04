package com.kasirkita.pos.data.model

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.kasirkita.pos.domain.model.CartItem

/** Shared sync DTO. Optional V1 fields are omitted by [CreateTransactionRequestAdapter]. */
@JsonAdapter(CreateTransactionRequestAdapter::class)
data class CreateTransactionRequest(
    @SerializedName("client_transaction_id")
    val clientTransactionId: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("customer_id")
    val customerId: String?,
    val items: List<CreateTransactionItemRequest>,
    val payment: PaymentRequest,
    @SerializedName("cashier_session_id")
    val cashierSessionId: String? = null,
    val discount: Long? = null,
)

data class CreateTransactionItemRequest(
    @SerializedName("product_id")
    val productId: String,
    val quantity: Int,
    @SerializedName("modifier_option_ids")
    val modifierOptionIds: List<String>? = null,
    val note: String? = null,
)

fun createTransactionRequest(
    clientTransactionId: String,
    outletId: String,
    customerId: String?,
    items: List<CartItem>,
    paymentAmount: Long,
): CreateTransactionRequest = CreateTransactionRequest(
    clientTransactionId = clientTransactionId,
    outletId = outletId,
    customerId = customerId,
    items = items.map { item ->
        CreateTransactionItemRequest(
            productId = item.productId,
            quantity = item.quantity,
        )
    },
    payment = PaymentRequest(
        method = "CASH",
        amount = paymentAmount,
    ),
)

class CreateTransactionRequestAdapter : TypeAdapter<CreateTransactionRequest>() {
    override fun write(out: JsonWriter, value: CreateTransactionRequest?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("client_transaction_id").value(value.clientTransactionId)
        out.name("outlet_id").value(value.outletId)
        value.cashierSessionId?.let { out.name("cashier_session_id").value(it) }
        value.customerId?.let { out.name("customer_id").value(it) }
        out.name("items").beginArray()
        value.items.forEach { item ->
            out.beginObject()
            out.name("product_id").value(item.productId)
            out.name("quantity").value(item.quantity)
            item.modifierOptionIds?.let { ids ->
                out.name("modifier_option_ids").beginArray()
                ids.forEach { out.value(it) }
                out.endArray()
            }
            item.note?.let { out.name("note").value(it) }
            out.endObject()
        }
        out.endArray()
        out.name("payment").beginObject()
        out.name("method").value(value.payment.method)
        value.payment.amount?.let { out.name("amount").value(it) }
        value.payment.amountReceived?.let { out.name("amount_received").value(it) }
        out.endObject()
        value.discount?.let { out.name("discount").value(it) }
        out.endObject()
    }

    override fun read(reader: JsonReader): CreateTransactionRequest {
        val root = com.google.gson.JsonParser.parseReader(reader).asJsonObject
        val gson = com.google.gson.Gson()
        return CreateTransactionRequest(
            clientTransactionId = root.get("client_transaction_id").asString,
            outletId = root.get("outlet_id").asString,
            customerId = root.get("customer_id")?.takeUnless { it.isJsonNull }?.asString,
            items = root.getAsJsonArray("items").map {
                gson.fromJson(it, CreateTransactionItemRequest::class.java)
            },
            payment = gson.fromJson(root.get("payment"), PaymentRequest::class.java),
            cashierSessionId = root.get("cashier_session_id")?.takeUnless { it.isJsonNull }?.asString,
            discount = root.get("discount")?.takeUnless { it.isJsonNull }?.asLong,
        )
    }
}
