package com.kasirkita.pos.presentation.receipt

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.repository.ReceiptRepository
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptViewModelTest {

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
    fun receiptLoadFailure_keepsTransactionSuccessMessage() = runTest(dispatcher) {
        val repository = FakeReceiptRepository(
            Result.failure(IOException("network unavailable")),
        )
        val viewModel = ReceiptViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("transactionId" to "transaction-id"),
            ),
            getReceipt = GetReceiptUseCase(repository),
        )

        advanceUntilIdle()

        assertEquals(
            ReceiptState.Error(RECEIPT_LOAD_ERROR_MESSAGE),
            viewModel.state.value,
        )
    }

    private class FakeReceiptRepository(
        private val result: Result<Receipt>,
    ) : ReceiptRepository {
        override suspend fun getReceipt(transactionId: String): Result<Receipt> = result
    }
}
