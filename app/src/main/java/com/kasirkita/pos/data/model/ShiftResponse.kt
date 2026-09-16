package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.Shift

data class ShiftResponse(
    @SerializedName("shift_id")
    val shiftId: String,
    @SerializedName("outlet_id")
    val outletId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("opening_cash")
    val openingCash: Long,
    @SerializedName("closing_cash")
    val closingCash: Long?,
    @SerializedName("expected_cash")
    val expectedCash: Long?,
    val difference: Long?,
    val status: String,
    @SerializedName("opened_at")
    val openedAt: String,
    @SerializedName("closed_at")
    val closedAt: String?,
)

fun ShiftResponse.toDomain(): Shift = Shift(
    id = shiftId,
    outletId = outletId,
    userId = userId,
    openingCash = openingCash,
    closingCash = closingCash,
    expectedCash = expectedCash,
    difference = difference,
    status = status,
    openedAt = openedAt,
    closedAt = closedAt,
)
