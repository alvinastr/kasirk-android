package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.ShiftApi
import com.kasirkita.pos.data.model.CloseShiftRequest
import com.kasirkita.pos.data.model.OpenShiftRequest
import com.kasirkita.pos.data.model.ShiftResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ShiftRepositoryImplTest {

    @Test
    fun getCurrentShift_mapsSuccessfulResponseAndPublishesCurrentShift() = runBlocking {
        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val repository = ShiftRepositoryImpl(api)

        val shift = repository.getCurrentShift().getOrThrow()

        assertEquals(SHIFT_ID, shift?.id)
        assertEquals(OUTLET_ID, shift?.outletId)
        assertEquals(50_000L, shift?.openingCash)
        assertEquals(shift, repository.currentShift.value)
    }

    @Test
    fun getCurrentShift_whenBackendReturns404_returnsNullWithoutFailure() = runBlocking {
        val api = FakeShiftApi(
            currentResponse = Response.error(
                404,
                "{}".toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = ShiftRepositoryImpl(api)

        val result = repository.getCurrentShift()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        assertNull(repository.currentShift.value)
    }

    @Test
    fun openShift_sendsRequestAndPublishesOpenedShift() = runBlocking {
        val api = FakeShiftApi(openResponse = Response.success(openShiftResponse()))
        val repository = ShiftRepositoryImpl(api)

        val shift = repository.openShift(OUTLET_ID, 50_000L).getOrThrow()

        assertEquals(
            OpenShiftRequest(OUTLET_ID, 50_000L),
            api.lastOpenRequest,
        )
        assertEquals("OPEN", shift.status)
        assertEquals(shift, repository.currentShift.value)
    }

    @Test
    fun closeShift_returnsReconciliationAndClearsCurrentShift() = runBlocking {
        val api = FakeShiftApi(
            currentResponse = Response.success(openShiftResponse()),
            closeResponse = Response.success(closedShiftResponse()),
        )
        val repository = ShiftRepositoryImpl(api)
        repository.getCurrentShift().getOrThrow()

        val shift = repository.closeShift(SHIFT_ID, 200_000L).getOrThrow()

        assertEquals(SHIFT_ID, api.lastClosedShiftId)
        assertEquals(CloseShiftRequest(200_000L), api.lastCloseRequest)
        assertEquals(210_000L, shift.expectedCash)
        assertEquals(-10_000L, shift.difference)
        assertEquals("CLOSED", shift.status)
        assertNull(repository.currentShift.value)
    }

    @Test
    fun getCurrentShift_whenTransportFails_returnsSameFailure() = runBlocking {
        val expected = IOException("network unavailable")
        val repository = ShiftRepositoryImpl(FakeShiftApi(currentFailure = expected))

        val result = repository.getCurrentShift()

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun clearCurrentShift_removesPublishedShiftWithoutApiCall() = runBlocking {
        val api = FakeShiftApi(currentResponse = Response.success(openShiftResponse()))
        val repository = ShiftRepositoryImpl(api)
        repository.getCurrentShift().getOrThrow()

        repository.clearCurrentShift()

        assertNull(repository.currentShift.value)
    }

    private class FakeShiftApi(
        private val currentResponse: Response<ShiftResponse> = Response.success(openShiftResponse()),
        private val openResponse: Response<ShiftResponse> = Response.success(openShiftResponse()),
        private val closeResponse: Response<ShiftResponse> = Response.success(closedShiftResponse()),
        private val currentFailure: Throwable? = null,
    ) : ShiftApi {
        var lastOpenRequest: OpenShiftRequest? = null
        var lastClosedShiftId: String? = null
        var lastCloseRequest: CloseShiftRequest? = null

        override suspend fun getCurrentShift(): Response<ShiftResponse> {
            currentFailure?.let { throw it }
            return currentResponse
        }

        override suspend fun openShift(request: OpenShiftRequest): Response<ShiftResponse> {
            lastOpenRequest = request
            return openResponse
        }

        override suspend fun closeShift(
            shiftId: String,
            request: CloseShiftRequest,
        ): Response<ShiftResponse> {
            lastClosedShiftId = shiftId
            lastCloseRequest = request
            return closeResponse
        }
    }

    private companion object {
        const val SHIFT_ID = "shift-id"
        const val OUTLET_ID = "outlet-id"

        fun openShiftResponse() = ShiftResponse(
            shiftId = SHIFT_ID,
            outletId = OUTLET_ID,
            userId = "user-id",
            openingCash = 50_000L,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = "OPEN",
            openedAt = "2026-09-24T08:00:00.000Z",
            closedAt = null,
        )

        fun closedShiftResponse() = openShiftResponse().copy(
            closingCash = 200_000L,
            expectedCash = 210_000L,
            difference = -10_000L,
            status = "CLOSED",
            closedAt = "2026-09-24T16:00:00.000Z",
        )
    }
}
