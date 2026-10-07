package com.kasirkita.pos.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/**
 * M16G-0B success envelope for `POST /held-orders/{id}/checkout`.
 *
 * [transaction] reuses the canonical [TransactionDetailResponse] verbatim; no canonical
 * transaction field is duplicated here.
 */
@JsonAdapter(HeldOrderCheckoutResponseDeserializer::class)
data class HeldOrderCheckoutResponse(
    @SerializedName("transaction")
    val transaction: TransactionDetailResponse,
    @SerializedName("replayed")
    val replayed: Boolean,
)

/**
 * Fail-fast deserializer for [HeldOrderCheckoutResponse].
 *
 * Gson binds an absent primitive `Boolean` to `false` and an absent object to `null`,
 * so reflective binding alone would read a missing `replayed` (or a legacy flat
 * transaction body) as `replayed = false`, i.e. as an original checkout eligible for
 * physical side effects. Both fields are required by the backend contract, so they are
 * required here: a malformed or missing-field envelope fails to parse instead.
 */
class HeldOrderCheckoutResponseDeserializer : JsonDeserializer<HeldOrderCheckoutResponse> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): HeldOrderCheckoutResponse {
        val root = json.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw JsonParseException(
                "Held order checkout response must be a JSON object containing 'transaction' and 'replayed'",
            )

        val transactionElement = root.get(TRANSACTION_KEY)
        if (transactionElement == null || !transactionElement.isJsonObject) {
            throw JsonParseException(
                "Held order checkout response is missing the required 'transaction' object",
            )
        }

        val replayedElement = root.get(REPLAYED_KEY)
        val replayed = replayedElement
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: throw JsonParseException(
                "Held order checkout response is missing the required boolean 'replayed' field",
            )

        val transaction: TransactionDetailResponse =
            context.deserialize(transactionElement, TransactionDetailResponse::class.java)
                ?: throw JsonParseException(
                "Held order checkout response 'transaction' could not be read as a canonical transaction",
            )

        return HeldOrderCheckoutResponse(
            transaction = transaction,
            replayed = replayed,
        )
    }

    companion object {
        private const val TRANSACTION_KEY = "transaction"
        private const val REPLAYED_KEY = "replayed"
    }
}
