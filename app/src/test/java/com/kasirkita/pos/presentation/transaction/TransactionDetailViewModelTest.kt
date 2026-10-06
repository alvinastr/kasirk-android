package com.kasirkita.pos.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.model.V1TransactionRequest
import com.kasirkita.pos.domain.repository.ReceiptRepository
import com.kasirkita.pos.domain.repository.TransactionRepository
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.GetTransactionDetailUseCase
import com.kasirkita.pos.domain.usecase.PrintReceiptUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val transactions = FakeTransactions()
    private val receipts = FakeReceipts()
    private val printer = FakePrinter()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun completedTransactionPrintsCanonicalReceipt() = runTest(dispatcher) {
        receipts.result = Result.success(receipt())
        transactions.detail = Result.success(transaction(status = "completed"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.printReceipt()
        advanceUntilIdle()

        assertEquals(listOf("server-transaction"), receipts.requestedIds)
        assertEquals(listOf(receipt()), printer.receipts)
        assertEquals(1, printer.invocations)
        assertEquals(TransactionPrintState.Success, viewModel.printState.value)
    }

    @Test
    fun printReceiptImmediatelyTransitionsToLoading() = runTest(dispatcher) {
        transactions.detail = Result.success(transaction(status = "completed"))
        receipts.suspendLoad = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.printReceipt()
        runCurrent()

        assertEquals(TransactionPrintState.Loading, viewModel.printState.value)
        assertEquals(listOf("server-transaction"), receipts.requestedIds)
        assertEquals(0, printer.invocations)
    }

    @Test
    fun retryAfterFailureStartsExactlyOneNewPrint() = runTest(dispatcher) {
        transactions.detail = Result.success(transaction(status = "completed"))
        receipts.result = Result.success(receipt())
        printer.result = Result.failure(IOException("printer off"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.printReceipt()
        advanceUntilIdle()
        assertEquals(TransactionPrintState.Error("printer off"), viewModel.printState.value)
        assertEquals(1, printer.invocations)

        viewModel.dismissPrintError()
        printer.result = Result.success(Unit)
        viewModel.printReceipt()
        advanceUntilIdle()

        assertEquals(2, printer.invocations)
        assertEquals(TransactionPrintState.Success, viewModel.printState.value)
    }

    @Test
    fun completedStatusIsCaseInsensitiveForReprint() = runTest(dispatcher) {
        receipts.result = Result.success(receipt())
        transactions.detail = Result.success(transaction(status = "COMPLETED"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.printReceipt()
        advanceUntilIdle()

        assertEquals(1, printer.invocations)
        assertEquals(TransactionPrintState.Success, viewModel.printState.value)
    }

    @Test
    fun nonCompletedTransactionDoesNotPrint() = runTest(dispatcher) {
        transactions.detail = Result.success(transaction(status = "pending"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.printReceipt()
        advanceUntilIdle()

        assertTrue(receipts.requestedIds.isEmpty())
        assertTrue(printer.receipts.isEmpty())
        assertEquals(TransactionPrintState.Idle, viewModel.printState.value)
    }

    @Test
    fun receiptLoadFailureDoesNotPrint() = runTest(dispatcher) {
        transactions.detail = Result.success(transaction(status = "completed"))
        receipts.result = Result.failure(IOException("offline"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.printReceipt()
        advanceUntilIdle()

        assertTrue(printer.receipts.isEmpty())
        assertTrue(viewModel.printState.value is TransactionPrintState.Error)
    }

    @Test
    fun printFailureSurfacesMessage() = runTest(dispatcher) {
        transactions.detail = Result.success(transaction(status = "completed"))
        receipts.result = Result.success(receipt())
        printer.result = Result.failure(IOException("printer off"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.printReceipt()
        advanceUntilIdle()

        assertEquals(TransactionPrintState.Error("printer off"), viewModel.printState.value)
    }

    @Test
    fun duplicateTapWhilePrintInFlightIsIgnored() = runTest(dispatcher) {
        transactions.detail = Result.success(transaction(status = "completed"))
        receipts.result = Result.success(receipt())
        receipts.suspendLoad = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.printReceipt()
        viewModel.printReceipt()
        runCurrent()

        assertEquals(listOf("server-transaction"), receipts.requestedIds)
        receipts.complete(Result.success(receipt()))
        advanceUntilIdle()
        assertEquals(1, printer.receipts.size)
    }

    @Test
    fun dismissPrintErrorReturnsToIdle() = runTest(dispatcher) {
        transactions.detail = Result.success(transaction(status = "completed"))
        receipts.result = Result.failure(IOException("offline"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.printReceipt()
        advanceUntilIdle()

        viewModel.dismissPrintError()

        assertEquals(TransactionPrintState.Idle, viewModel.printState.value)
    }

    private fun viewModel() = TransactionDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("transactionId" to "server-transaction")),
        getTransactionDetail = GetTransactionDetailUseCase(transactions),
        getReceipt = GetReceiptUseCase(receipts),
        printReceipt = printer,
    )

    private fun transaction(status: String) = Transaction(
        id = "server-transaction",
        clientTransactionId = "client-transaction",
        outletId = "outlet",
        userId = "cashier",
        customerId = null,
        status = status,
        subtotal = 10_000,
        discount = 0,
        tax = 0,
        total = 10_000,
        items = emptyList(),
        payments = emptyList(),
        change = 0,
        createdAt = "2026-10-06T00:00:00Z",
    )

    private fun receipt() = Receipt(
        transactionId = "server-transaction",
        clientTransactionId = "client-transaction",
        status = "completed",
        createdAt = "2026-10-06T00:00:00Z",
        tenant = ReceiptTenant("tenant", "Tenant", null),
        outlet = ReceiptOutlet("outlet", "Outlet", null),
        cashier = ReceiptCashier("cashier", "Cashier"),
        customer = null,
        items = emptyList(),
        payment = null,
        subtotal = 10_000,
        discount = 0,
        tax = 0,
        total = 10_000,
        change = 0,
    )

    private class FakeReceipts : ReceiptRepository {
        val requestedIds = mutableListOf<String>()
        var result: Result<Receipt> = Result.failure(IllegalStateException("Set receipt result"))
        var suspendLoad = false
        private var deferred: CompletableDeferred<Result<Receipt>>? = null

        override suspend fun getReceipt(transactionId: String): Result<Receipt> {
            requestedIds += transactionId
            if (!suspendLoad) return result
            return CompletableDeferred<Result<Receipt>>().also { deferred = it }.await()
        }

        fun complete(result: Result<Receipt>) {
            deferred?.complete(result)
        }
    }

    private class FakePrinter : PrintReceiptUseCase() {
        val receipts = mutableListOf<Receipt>()
        var result: Result<Unit> = Result.success(Unit)
        var invocations = 0

        override suspend fun invoke(receipt: Receipt): Result<Unit> {
            invocations += 1
            receipts += receipt
            return result
        }
    }

    private class FakeTransactions : TransactionRepository {
        var detail: Result<Transaction> = Result.failure(IllegalStateException("Set detail result"))

        override suspend fun getTransactionDetail(transactionId: String): Result<Transaction> = detail

        override suspend fun getTransactions(from: String?, to: String?): Result<List<Transaction>> =
            error("Unused")

        override suspend fun createV1Transaction(request: V1TransactionRequest): Result<Transaction> =
            error("Unused")

        override suspend fun createTransaction(
            clientTransactionId: String,
            outletId: String,
            customerId: String?,
            items: List<CartItem>,
            paymentAmount: Long,
        ): Result<Transaction> = error("Unused")
    }
}