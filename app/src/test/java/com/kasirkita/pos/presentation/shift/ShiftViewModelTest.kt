package com.kasirkita.pos.presentation.shift

import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.usecase.CloseShiftUseCase
import com.kasirkita.pos.domain.usecase.GetCurrentShiftUseCase
import com.kasirkita.pos.domain.usecase.OpenShiftUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cachedOpenShift_survivesOfflineRefresh() {
        val cachedShift = openShift()
        val repository = FakeShiftRepository(
            cachedShift = cachedShift,
            getCurrentShiftResult = Result.failure(IOException("offline")),
        )
        val viewModel = shiftViewModel(repository)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ShiftState.ShiftLoaded(cachedShift), viewModel.state.value)
        assertEquals(1, repository.getCurrentShiftCalls)
    }

    @Test
    fun noCachedShift_offlineRefreshShowsRetryableError() {
        val repository = FakeShiftRepository(
            cachedShift = null,
            getCurrentShiftResult = Result.failure(IOException("offline")),
        )
        val viewModel = shiftViewModel(repository)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            ShiftState.Error("Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."),
            viewModel.state.value,
        )
    }

    private fun shiftViewModel(repository: FakeShiftRepository) = ShiftViewModel(
        getCurrentShiftUseCase = GetCurrentShiftUseCase(repository),
        openShiftUseCase = OpenShiftUseCase(repository),
        closeShiftUseCase = CloseShiftUseCase(repository),
        shiftRepository = repository,
    )

    private class FakeShiftRepository(
        cachedShift: Shift?,
        private val getCurrentShiftResult: Result<Shift?>,
    ) : ShiftRepository {
        private val shiftState = MutableStateFlow(cachedShift)
        override val currentShift: StateFlow<Shift?> = shiftState
        var getCurrentShiftCalls = 0

        override suspend fun getCurrentShift(): Result<Shift?> {
            getCurrentShiftCalls += 1
            return getCurrentShiftResult
        }

        override suspend fun openShift(outletId: String, openingCash: Long): Result<Shift> =
            error("Not used")

        override suspend fun closeShift(shiftId: String, closingCash: Long): Result<Shift> =
            error("Not used")

        override suspend fun clearCurrentShift(tenantId: String?, userId: String?) {
            shiftState.value = null
        }

        override suspend fun restoreCurrentShift(expectedOutletId: String?) = Unit
    }

    private companion object {
        fun openShift() = Shift(
            id = "shift-id",
            outletId = "outlet-id",
            userId = "user-id",
            openingCash = 50_000L,
            closingCash = null,
            expectedCash = null,
            difference = null,
            status = "OPEN",
            openedAt = "2026-09-24T08:00:00.000Z",
            closedAt = null,
        )
    }
}
