package com.kasirkita.pos.data.model

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptSettingsUpdate
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings

/**
 * Request body for PUT /outlets/{outletId}/receipt-settings.
 *
 * Carries exactly the 12 editable fields. `tenant_id`, `outlet_id`,
 * `template_version`, `created_at`, and `updated_at` are absent by design: the
 * backend rejects unknown properties (`forbidNonWhitelisted`) and derives them
 * server-side.
 *
 * [UpdateReceiptSettingsRequestAdapter] writes every field unconditionally,
 * including an explicit JSON `null` for the nullable ones. The backend rejects
 * missing keys with 400, and Retrofit copies the transport Gson's
 * `serializeNulls=false` onto the JsonWriter, so a plain reflective serializer
 * would drop those keys.
 */
@JsonAdapter(UpdateReceiptSettingsRequestAdapter::class)
data class UpdateReceiptSettingsRequest(
    @SerializedName("header_store_name")
    val headerStoreName: String,
    @SerializedName("header_outlet_name")
    val headerOutletName: String,
    @SerializedName("header_address")
    val headerAddress: String?,
    @SerializedName("header_phone")
    val headerPhone: String?,
    @SerializedName("header_additional_text")
    val headerAdditionalText: String?,
    @SerializedName("show_sku")
    val showSku: Boolean,
    @SerializedName("show_modifiers")
    val showModifiers: Boolean,
    @SerializedName("show_item_notes")
    val showItemNotes: Boolean,
    @SerializedName("show_cashier")
    val showCashier: Boolean,
    @SerializedName("show_customer")
    val showCustomer: Boolean,
    @SerializedName("footer_thank_you_text")
    val footerThankYouText: String,
    @SerializedName("footer_promo_text")
    val footerPromoText: String?,
)

class UpdateReceiptSettingsRequestAdapter : TypeAdapter<UpdateReceiptSettingsRequest>() {

    override fun write(out: JsonWriter, value: UpdateReceiptSettingsRequest?) {
        if (value == null) {
            out.nullValue()
            return
        }
        // Required so the nullable fields are emitted as `"field": null`.
        val previousSerializeNulls = out.serializeNulls
        out.serializeNulls = true
        try {
            out.beginObject()
            out.name("header_store_name").value(value.headerStoreName)
            out.name("header_outlet_name").value(value.headerOutletName)
            out.name("header_address").value(value.headerAddress)
            out.name("header_phone").value(value.headerPhone)
            out.name("header_additional_text").value(value.headerAdditionalText)
            out.name("show_sku").value(value.showSku)
            out.name("show_modifiers").value(value.showModifiers)
            out.name("show_item_notes").value(value.showItemNotes)
            out.name("show_cashier").value(value.showCashier)
            out.name("show_customer").value(value.showCustomer)
            out.name("footer_thank_you_text").value(value.footerThankYouText)
            out.name("footer_promo_text").value(value.footerPromoText)
            out.endObject()
        } finally {
            out.serializeNulls = previousSerializeNulls
        }
    }

    override fun read(reader: JsonReader): UpdateReceiptSettingsRequest {
        val root = com.google.gson.JsonParser.parseReader(reader).asJsonObject
        return UpdateReceiptSettingsRequest(
            headerStoreName = root.get("header_store_name").asString,
            headerOutletName = root.get("header_outlet_name").asString,
            headerAddress = root.get("header_address")?.takeUnless { it.isJsonNull }?.asString,
            headerPhone = root.get("header_phone")?.takeUnless { it.isJsonNull }?.asString,
            headerAdditionalText = root.get("header_additional_text")
                ?.takeUnless { it.isJsonNull }?.asString,
            showSku = root.get("show_sku").asBoolean,
            showModifiers = root.get("show_modifiers").asBoolean,
            showItemNotes = root.get("show_item_notes").asBoolean,
            showCashier = root.get("show_cashier").asBoolean,
            showCustomer = root.get("show_customer").asBoolean,
            footerThankYouText = root.get("footer_thank_you_text").asString,
            footerPromoText = root.get("footer_promo_text")?.takeUnless { it.isJsonNull }?.asString,
        )
    }
}

/** Maps the domain update model into its wire request DTO. */
fun ReceiptSettingsUpdate.toData(): UpdateReceiptSettingsRequest = UpdateReceiptSettingsRequest(
    headerStoreName = headerStoreName,
    headerOutletName = headerOutletName,
    headerAddress = headerAddress,
    headerPhone = headerPhone,
    headerAdditionalText = headerAdditionalText,
    showSku = showSku,
    showModifiers = showModifiers,
    showItemNotes = showItemNotes,
    showCashier = showCashier,
    showCustomer = showCustomer,
    footerThankYouText = footerThankYouText,
    footerPromoText = footerPromoText,
)

/**
 * Response body for GET/PUT /outlets/{outletId}/receipt-settings.
 *
 * Mirrors the backend ReceiptSettingsResponseDto field-for-field.
 */
data class ReceiptSettingsResponse(
    @SerializedName("tenant_id")
    val tenantId: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("header_store_name")
    val headerStoreName: String,
    @SerializedName("header_outlet_name")
    val headerOutletName: String,
    @SerializedName("header_address")
    val headerAddress: String?,
    @SerializedName("header_phone")
    val headerPhone: String?,
    @SerializedName("header_additional_text")
    val headerAdditionalText: String?,
    @SerializedName("show_sku")
    val showSku: Boolean,
    @SerializedName("show_modifiers")
    val showModifiers: Boolean,
    @SerializedName("show_item_notes")
    val showItemNotes: Boolean,
    @SerializedName("show_cashier")
    val showCashier: Boolean,
    @SerializedName("show_customer")
    val showCustomer: Boolean,
    @SerializedName("footer_thank_you_text")
    val footerThankYouText: String,
    @SerializedName("footer_promo_text")
    val footerPromoText: String?,
    @SerializedName("template_version")
    val templateVersion: Int,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?,
)

/**
 * Maps the wire DTO to the domain model, preserving nullable values.
 * A JSON null maps to null, never to a synthesized default.
 */
fun ReceiptSettingsResponse.toDomain(): ReceiptSettings = ReceiptSettings(
    tenantId = tenantId,
    outletId = outletId,
    header = ReceiptHeaderSettings(
        storeName = headerStoreName,
        outletName = headerOutletName,
        address = headerAddress,
        phone = headerPhone,
        additionalText = headerAdditionalText,
    ),
    visibility = ReceiptVisibilitySettings(
        showSku = showSku,
        showModifiers = showModifiers,
        showItemNotes = showItemNotes,
        showCashier = showCashier,
        showCustomer = showCustomer,
    ),
    footer = ReceiptFooterSettings(
        thankYouText = footerThankYouText,
        promoText = footerPromoText,
    ),
    templateVersion = templateVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Gson used for cache persistence only; never used for transport. */
internal val ReceiptSettingsCacheGson: com.google.gson.Gson = GsonBuilder().create()