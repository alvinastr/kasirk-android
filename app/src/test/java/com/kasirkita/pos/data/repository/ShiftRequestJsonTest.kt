package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.CloseShiftRequest
import com.kasirkita.pos.data.model.OpenShiftRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftRequestJsonTest {

    private val gson = Gson()

    @Test
    fun openShiftRequest_sendsOnlyOutletIdInBackendSnakeCase() {
        val json = gson.toJsonTree(
            OpenShiftRequest(outletId = "outlet-id"),
        ).asJsonObject

        assertEquals("outlet-id", json.get("outlet_id").asString)
        assertFalse(json.has("opening_cash"))
        assertFalse(json.has("openingCash"))
        assertFalse(json.has("outletId"))
        assertEquals(1, json.size())
    }

    @Test
    fun closeShiftRequest_serializesToEmptyObject() {
        val json = gson.toJsonTree(CloseShiftRequest()).asJsonObject

        assertEquals(0, json.size())
        assertFalse(json.has("closing_cash"))
        assertFalse(json.has("closingCash"))
    }

    @Test
    fun closeShiftRequest_omitsClosingCashWhenNullByDefault() {
        val request = CloseShiftRequest()
        val serialized = gson.toJson(request)

        // V1 close contract: backend CloseShiftDto is empty, so the default
        // request must not carry any cash payload. Gson omits null fields.
        assertEquals("{}", serialized)
    }

    @Test
    fun closeShiftRequest_remainsEmptyWithSerializeNullsEnabled() {
        val gsonIncludingNulls = com.google.gson.GsonBuilder().serializeNulls().create()
        assertEquals("{}", gsonIncludingNulls.toJson(CloseShiftRequest()))
    }

    @Test
    fun openShiftRequest_outletIdIsRequiredAndSerializedAsUuidField() {
        val request = OpenShiftRequest(outletId = "550e8400-e29b-41d4-a716-446655440000")
        val json = gson.toJsonTree(request).asJsonObject

        assertTrue(json.has("outlet_id"))
        assertEquals("550e8400-e29b-41d4-a716-446655440000", json.get("outlet_id").asString)
    }
}
