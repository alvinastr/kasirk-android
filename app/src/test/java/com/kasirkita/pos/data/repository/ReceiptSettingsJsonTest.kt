package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonWriter
import com.kasirkita.pos.data.model.ReceiptSettingsResponse
import com.kasirkita.pos.data.model.UpdateReceiptSettingsRequest
import com.kasirkita.pos.data.model.toData
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptSettingsJsonTest {

    private val gson = Gson()

    // --- GET response deserialization (backend contract) ---

    @Test
    fun `GET response deserializes all fields including nullable`() {
        val json = """{
            "tenant_id": "t-1",
            "outlet_id": "o-1",
            "header_store_name": "My Store",
            "header_outlet_name": "My Outlet",
            "header_address": "123 Main St",
            "header_phone": "555-0100",
            "header_additional_text": "Open daily",
            "show_sku": true,
            "show_modifiers": false,
            "show_item_notes": true,
            "show_cashier": true,
            "show_customer": false,
            "footer_thank_you_text": "Thank you!",
            "footer_promo_text": "10% off",
            "template_version": 42,
            "created_at": "2026-10-01T10:00:00.000Z",
            "updated_at": "2026-10-08T12:00:00.000Z"
        }"""
        val response = gson.fromJson(json, ReceiptSettingsResponse::class.java)
        assertEquals("t-1", response.tenantId)
        assertEquals("o-1", response.outletId)
        assertEquals("My Store", response.headerStoreName)
        assertEquals("My Outlet", response.headerOutletName)
        assertEquals("123 Main St", response.headerAddress)
        assertEquals("555-0100", response.headerPhone)
        assertEquals("Open daily", response.headerAdditionalText)
        assertTrue(response.showSku)
        assertFalse(response.showModifiers)
        assertTrue(response.showItemNotes)
        assertTrue(response.showCashier)
        assertFalse(response.showCustomer)
        assertEquals("Thank you!", response.footerThankYouText)
        assertEquals("10% off", response.footerPromoText)
        assertEquals(42, response.templateVersion)
        assertEquals("2026-10-01T10:00:00.000Z", response.createdAt)
        assertEquals("2026-10-08T12:00:00.000Z", response.updatedAt)
    }

    @Test
    fun `GET response maps null nullable fields to null`() {
        val json = """{
            "tenant_id": "t-1",
            "outlet_id": "o-1",
            "header_store_name": "Store",
            "header_outlet_name": "Outlet",
            "header_address": null,
            "header_phone": null,
            "header_additional_text": null,
            "show_sku": true,
            "show_modifiers": false,
            "show_item_notes": true,
            "show_cashier": true,
            "show_customer": false,
            "footer_thank_you_text": "Thanks",
            "footer_promo_text": null,
            "template_version": 1,
            "created_at": null,
            "updated_at": null
        }"""
        val response = gson.fromJson(json, ReceiptSettingsResponse::class.java)
        assertNull(response.headerAddress)
        assertNull(response.headerPhone)
        assertNull(response.headerAdditionalText)
        assertNull(response.footerPromoText)
        assertNull(response.createdAt)
        assertNull(response.updatedAt)
    }

    @Test
    fun `GET response toDomain preserves nullability`() {
        val json = """{
            "tenant_id": "t-1",
            "outlet_id": "o-1",
            "header_store_name": "S",
            "header_outlet_name": "O",
            "header_address": null,
            "header_phone": null,
            "header_additional_text": null,
            "show_sku": false,
            "show_modifiers": false,
            "show_item_notes": false,
            "show_cashier": false,
            "show_customer": false,
            "footer_thank_you_text": "T",
            "footer_promo_text": null,
            "template_version": 99,
            "created_at": "2026-01-01T00:00:00.000Z",
            "updated_at": "2026-01-02T00:00:00.000Z"
        }"""
        val domain = gson.fromJson(json, ReceiptSettingsResponse::class.java).toDomain()
        assertNull(domain.header.address)
        assertNull(domain.header.phone)
        assertNull(domain.header.additionalText)
        assertNull(domain.footer.promoText)
        assertEquals("2026-01-01T00:00:00.000Z", domain.createdAt)
        assertEquals("2026-01-02T00:00:00.000Z", domain.updatedAt)
        assertEquals("S", domain.header.storeName)
        assertEquals("O", domain.header.outletName)
        assertEquals("T", domain.footer.thankYouText)
        assertEquals(99, domain.templateVersion)
    }

    // --- PUT request serialization ---

    @Test
    fun `PUT request serializes all 12 editable fields`() {
        val request = UpdateReceiptSettingsRequest(
            headerStoreName = "Store",
            headerOutletName = "Outlet",
            headerAddress = "456 Oak Rd",
            headerPhone = "555-0200",
            headerAdditionalText = "Note",
            showSku = true,
            showModifiers = false,
            showItemNotes = true,
            showCashier = true,
            showCustomer = false,
            footerThankYouText = "Thanks",
            footerPromoText = "Promo"
        )
        val json = gson.toJson(request, UpdateReceiptSettingsRequest::class.java)
        // All 12 fields present
        assertTrue(json.contains("\"header_store_name\""))
        assertTrue(json.contains("\"header_outlet_name\""))
        assertTrue(json.contains("\"header_address\""))
        assertTrue(json.contains("\"header_phone\""))
        assertTrue(json.contains("\"header_additional_text\""))
        assertTrue(json.contains("\"show_sku\""))
        assertTrue(json.contains("\"show_modifiers\""))
        assertTrue(json.contains("\"show_item_notes\""))
        assertTrue(json.contains("\"show_cashier\""))
        assertTrue(json.contains("\"show_customer\""))
        assertTrue(json.contains("\"footer_thank_you_text\""))
        assertTrue(json.contains("\"footer_promo_text\""))
        // Server-owned fields absent
        assertFalse(json.contains("\"tenant_id\""))
        assertFalse(json.contains("\"outlet_id\""))
        assertFalse(json.contains("\"template_version\""))
        assertFalse(json.contains("\"created_at\""))
        assertFalse(json.contains("\"updated_at\""))
    }

    @Test
    fun `PUT request serializes nullable fields as explicit null`() {
        val request = UpdateReceiptSettingsRequest(
            headerStoreName = "Store",
            headerOutletName = "Outlet",
            headerAddress = null,
            headerPhone = null,
            headerAdditionalText = null,
            showSku = true,
            showModifiers = true,
            showItemNotes = true,
            showCashier = true,
            showCustomer = true,
            footerThankYouText = "Thanks",
            footerPromoText = null
        )
        val json = gson.toJson(request, UpdateReceiptSettingsRequest::class.java)
        assertTrue(json.contains("\"header_address\":null"))
        assertTrue(json.contains("\"header_phone\":null"))
        assertTrue(json.contains("\"header_additional_text\":null"))
        assertTrue(json.contains("\"footer_promo_text\":null"))
    }

    @Test
    fun `PUT adapter restores previous JsonWriter serializeNulls state`() {
        val gson = GsonBuilder()
            .registerTypeAdapter(Wrapper::class.java, WrapperAdapter())
            .create()
        val json = gson.toJson(
            Wrapper(
                request = UpdateReceiptSettingsRequest(
                    headerStoreName = "Store",
                    headerOutletName = "Outlet",
                    headerAddress = null,
                    headerPhone = null,
                    headerAdditionalText = null,
                    showSku = true,
                    showModifiers = true,
                    showItemNotes = true,
                    showCashier = true,
                    showCustomer = true,
                    footerThankYouText = "Thanks",
                    footerPromoText = null,
                ),
            ),
        )

        assertTrue(json.contains("\"header_address\":null"))
        assertFalse(json.contains("\"after\":null"))
    }

    // --- Domain model round-trip ---

    @Test
    fun `domain model toData and back round-trips`() {
        val update = com.kasirkita.pos.domain.model.ReceiptSettingsUpdate(
            headerStoreName = "Store",
            headerOutletName = "Outlet",
            headerAddress = "789 Elm St",
            headerPhone = null,
            headerAdditionalText = "Note",
            showSku = true,
            showModifiers = false,
            showItemNotes = true,
            showCashier = false,
            showCustomer = true,
            footerThankYouText = "Thanks",
            footerPromoText = null
        )
        val request = update.toData()
        assertEquals(update.headerStoreName, request.headerStoreName)
        assertEquals(update.headerOutletName, request.headerOutletName)
        assertEquals(update.headerAddress, request.headerAddress)
        assertNull(request.headerPhone)
        assertEquals(update.headerAdditionalText, request.headerAdditionalText)
        assertEquals(update.showSku, request.showSku)
        assertEquals(update.showModifiers, request.showModifiers)
        assertEquals(update.showItemNotes, request.showItemNotes)
        assertEquals(update.showCashier, request.showCashier)
        assertEquals(update.showCustomer, request.showCustomer)
        assertEquals(update.footerThankYouText, request.footerThankYouText)
        assertEquals(update.footerPromoText, request.footerPromoText)
    }

    // --- Domain model fields ---

    @Test
    fun `domain ReceiptSettings has all fields`() {
        val settings = ReceiptSettings(
            tenantId = "t-1",
            outletId = "o-1",
            header = ReceiptHeaderSettings(
                storeName = "Store",
                outletName = "Outlet",
                address = "123 St",
                phone = "555",
                additionalText = "Note"
            ),
            visibility = ReceiptVisibilitySettings(
                showSku = true,
                showModifiers = false,
                showItemNotes = true,
                showCashier = false,
                showCustomer = true
            ),
            footer = ReceiptFooterSettings(
                thankYouText = "Thanks",
                promoText = "Promo"
            ),
            templateVersion = 5,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-08T00:00:00Z"
        )
        assertEquals("t-1", settings.tenantId)
        assertEquals("o-1", settings.outletId)
        assertEquals("Store", settings.header.storeName)
        assertEquals("Outlet", settings.header.outletName)
        assertEquals("123 St", settings.header.address)
        assertEquals("555", settings.header.phone)
        assertEquals("Note", settings.header.additionalText)
        assertTrue(settings.visibility.showSku)
        assertFalse(settings.visibility.showModifiers)
        assertTrue(settings.visibility.showItemNotes)
        assertFalse(settings.visibility.showCashier)
        assertTrue(settings.visibility.showCustomer)
        assertEquals("Thanks", settings.footer.thankYouText)
        assertEquals("Promo", settings.footer.promoText)
        assertEquals(5, settings.templateVersion)
        assertEquals("2026-10-01T00:00:00Z", settings.createdAt)
        assertEquals("2026-10-08T00:00:00Z", settings.updatedAt)
    }

    private data class Wrapper(
        val request: UpdateReceiptSettingsRequest,
    )

    private class WrapperAdapter : TypeAdapter<Wrapper>() {
        override fun write(out: JsonWriter, value: Wrapper?) {
            out.beginObject()
            out.name("request")
            Gson().toJson(value?.request, UpdateReceiptSettingsRequest::class.java, out)
            out.name("after").nullValue()
            out.endObject()
        }

        override fun read(reader: com.google.gson.stream.JsonReader): Wrapper {
            throw UnsupportedOperationException()
        }
    }
}