package com.kasirkita.pos.data.repository

import com.google.gson.Gson
import com.kasirkita.pos.data.model.CloseShiftRequest
import com.kasirkita.pos.data.model.OpenShiftRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShiftRequestJsonTest {

    private val gson = Gson()

    @Test
    fun openShiftRequest_usesBackendSnakeCaseFields() {
        val json = gson.toJsonTree(
            OpenShiftRequest(
                outletId = "outlet-id",
                openingCash = 50_000L,
            ),
        ).asJsonObject

        assertEquals("outlet-id", json.get("outlet_id").asString)
        assertEquals(50_000L, json.get("opening_cash").asLong)
        assertFalse(json.has("outletId"))
        assertFalse(json.has("openingCash"))
    }

    @Test
    fun closeShiftRequest_usesBackendSnakeCaseField() {
        val json = gson.toJsonTree(
            CloseShiftRequest(closingCash = 200_000L),
        ).asJsonObject

        assertEquals(200_000L, json.get("closing_cash").asLong)
        assertFalse(json.has("closingCash"))
    }
}
