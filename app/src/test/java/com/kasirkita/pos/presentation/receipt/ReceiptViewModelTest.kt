package com.kasirkita.pos.presentation.receipt

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.repository.ReceiptRepository
import com.kasirkita.pos.domain.usecase.BuildReceiptDocumentUseCase
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.PrintReceiptUseCase
import com.kasirkita.pos.domain.usecase.ResolveReceiptSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun receiptLoadFailure_showsError() = runTest(dispatcher) {
        val repository = FakeReceiptRepository(
            Result.failure(IOException("network unavailable")),
        )
        val viewModel = ReceiptViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("transactionId" to "transaction-id"),
            ),
            getReceipt = GetReceiptUseCase(repository),
            printReceipt = FakePrintReceiptUseCase(),
            resolveSettings = { _ -> ResolveReceiptSettingsUseCase.Resolution(null, true) },
            paperWidthProvider = { 58 },
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ReceiptState.Error)
    }

    @Test
    fun receiptSuccessWithSettings_buildsDocument() = runTest(dispatcher) {
        val receipt = sampleReceipt()
        val settings = ReceiptSettings(
            tenantId = "tenant",
            outletId = "outlet",
            header = com.kasirkita.pos.domain.model.ReceiptHeaderSettings(
                storeName = "Custom Store",
                outletName = "Custom Outlet",
                address = null,
                phone = null,
                additionalText = null,
            ),
            visibility = com.kasirkita.pos.domain.model.ReceiptVisibilitySettings(
                showSku = true,
                showModifiers = true,
                showItemNotes = true,
                showCashier = true,
                showCustomer = true,
            ),
            footer = com.kasirkita.pos.domain.model.ReceiptFooterSettings(
                thankYouText = "Thanks",
                promoText = null,
            ),
            templateVersion = 1,
            createdAt = null,
            updatedAt = null,
        )

        val repository = FakeReceiptRepository(Result.success(receipt))
        val resolveSettingsCall = mutableListOf<Receipt>()

        val viewModel = ReceiptViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "tx-1")),
            getReceipt = GetReceiptUseCase(repository),
            printReceipt = FakePrintReceiptUseCase(),
            resolveSettings = { receipt ->
                resolveSettingsCall.add(receipt)
                ResolveReceiptSettingsUseCase.Resolution(settings, false)
            },
            paperWidthProvider = { 58 },
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ReceiptState.Success)
        val success = state as ReceiptState.Success
        assertEquals(settings, success.settings)
        assertEquals(58, success.paperWidthMm)
        assertEquals(false, success.usedLegacyFallback)
        assertNotNull(success.document)
        assertEquals(1, resolveSettingsCall.size)
        assertEquals(receipt.tenant.id, resolveSettingsCall[0].tenant.id)
        assertEquals(receipt.outlet.id, resolveSettingsCall[0].outlet.id)
    }

    @Test
    fun receiptSuccessSettingsFailure_showsLegacyFallbackAndAllowsPrint() = runTest(dispatcher) {
        val receipt = sampleReceipt()

        val repository = FakeReceiptRepository(Result.success(receipt))

        val viewModel = ReceiptViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "tx-1")),
            getReceipt = GetReceiptUseCase(repository),
            printReceipt = FakePrintReceiptUseCase(),
            resolveSettings = { _ -> ResolveReceiptSettingsUseCase.Resolution(null, true) },
            paperWidthProvider = { 58 },
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ReceiptState.Success)
        val success = state as ReceiptState.Success
        assertNull(success.settings)
        assertEquals(58, success.paperWidthMm)
        assertEquals(true, success.usedLegacyFallback)
        assertNotNull(success.document)
    }

    @Test
    fun manualPrint_usesSameSettingsAndPaperWidthAsPreview() = runTest(dispatcher) {
        val receipt = sampleReceipt()
        val settings = ReceiptSettings(
            tenantId = "tenant",
            outletId = "outlet",
            header = com.kasirkita.pos.domain.model.ReceiptHeaderSettings(
                storeName = "Custom Store",
                outletName = "Custom Outlet",
                address = null,
                phone = null,
                additionalText = null,
            ),
            visibility = com.kasirkita.pos.domain.model.ReceiptVisibilitySettings(
                showSku = true,
                showModifiers = true,
                showItemNotes = true,
                showCashier = true,
                showCustomer = true,
            ),
            footer = com.kasirkita.pos.domain.model.ReceiptFooterSettings(
                thankYouText = "Thanks",
                promoText = null,
            ),
            templateVersion = 1,
            createdAt = null,
            updatedAt = null,
        )

        val repository = FakeReceiptRepository(Result.success(receipt))
        val printer = FakePrintReceiptUseCase()
        var resolveSettingsCallCount = 0

        val viewModel = ReceiptViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "tx-1")),
            getReceipt = GetReceiptUseCase(repository),
            printReceipt = printer,
            resolveSettings = { receipt ->
                resolveSettingsCallCount++
                ResolveReceiptSettingsUseCase.Resolution(settings, false)
            },
            paperWidthProvider = { 80 },
        )

        advanceUntilIdle()

        val previewState = viewModel.state.value as ReceiptState.Success
        assertEquals(settings, previewState.settings)
        assertEquals(80, previewState.paperWidthMm)

        // Manual print - same validated preview context and paper width
        viewModel.printReceipt(
            onPrintStart = {},
            onPrintComplete = {},
            onPrintError = {},
        )
        advanceUntilIdle()

        assertEquals(1, resolveSettingsCallCount) // no second refresh
        assertEquals(settings, printer.lastSettings)
        assertEquals(80, printer.lastWidth)
    }

    @Test
    fun printTransportFailure_isPrintFailureNotTransactionRollback() = runTest(dispatcher) {
        val receipt = sampleReceipt()
        val repository = FakeReceiptRepository(Result.success(receipt))
        val printer = FakePrintReceiptUseCase().apply { result = Result.failure(IOException("bluetooth disconnected")) }

        val viewModel = ReceiptViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "tx-1")),
            getReceipt = GetReceiptUseCase(repository),
            printReceipt = printer,
            resolveSettings = { _ -> ResolveReceiptSettingsUseCase.Resolution(null, true) },
            paperWidthProvider = { 58 },
        )

        advanceUntilIdle()

        var errorMessage: String? = null
        viewModel.printReceipt(
            onPrintStart = {},
            onPrintComplete = {},
            onPrintError = { errorMessage = it },
        )
        advanceUntilIdle()

        assertNotNull(errorMessage)
        assertTrue(errorMessage!!.contains("bluetooth disconnected"))
    }

    private fun sampleReceipt() = Receipt(
        transactionId = "tx-1",
        clientTransactionId = "client-1",
        status = "completed",
        createdAt = "2026-10-06T00:00:00Z",
        tenant = ReceiptTenant("tenant", "Tenant", null),
        outlet = ReceiptOutlet("outlet", "Outlet", null),
        cashier = ReceiptCashier("cashier", "Kasir"),
        customer = ReceiptCustomer("cust-1", "Pelanggan", null, null),
        items = emptyList(),
        payment = null,
        subtotal = 10_000,
        discount = 0,
        tax = 0,
        total = 10_000,
        change = null,
    )

    private class FakeReceiptRepository(
        private val result: Result<Receipt>,
    ) : ReceiptRepository {
        override suspend fun getReceipt(transactionId: String): Result<Receipt> = result
    }

    private class FakePrintReceiptUseCase : PrintReceiptUseCase() {
        var result: Result<Unit> = Result.success(Unit)
        var lastSettings: ReceiptSettings? = null
        var lastWidth: Int? = null

        override suspend fun invoke(receipt: Receipt): Result<Unit> = result

        override suspend fun invokeResolved(
            context: PrintReceiptUseCase.ValidatedPrintContext,
        ): Result<Unit> {
            lastSettings = context.settings
            lastWidth = context.paperWidthMm
            return result
        }
    }
}