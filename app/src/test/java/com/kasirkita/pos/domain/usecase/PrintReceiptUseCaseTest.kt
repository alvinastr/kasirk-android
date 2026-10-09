package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PrintReceiptUseCaseTest {

    @Test
    fun unresolvedReprintPassesReceiptIdentityToResolverExactlyOnce() = runTest {
        val fixture = Fixture()
        val receipt = sampleReceipt(tenantId = "tenant-old", outletId = "outlet-old")
        fixture.resolution = ResolveReceiptSettingsUseCase.Resolution(
            settings(tenantId = "tenant-old", outletId = "outlet-old"),
            false,
        )

        val result = fixture.useCase()(receipt)

        assertTrue(result.isSuccess)
        assertEquals(listOf("tenant-old" to "outlet-old"), fixture.resolverCalls)
        assertEquals(1, fixture.transportCalls)
        assertEquals(settings(tenantId = "tenant-old", outletId = "outlet-old"), fixture.lastFormattedSettings)
    }

    @Test
    fun matchingResolvedSettingsPrintSuccessfully() = runTest {
        val fixture = Fixture()
        val receipt = sampleReceipt()
        fixture.resolution = ResolveReceiptSettingsUseCase.Resolution(settings(), false)

        val result = fixture.useCase()(receipt)

        assertTrue(result.isSuccess)
        assertEquals(settings(), fixture.lastFormattedSettings)
        assertEquals(80, fixture.lastFormattedWidth)
        assertEquals(1, fixture.transportCalls)
    }

    @Test
    fun mismatchedTenantSettingsRejectedBeforeFormattingAndTransport() = runTest {
        val fixture = Fixture()
        val receipt = sampleReceipt()
        fixture.resolution = ResolveReceiptSettingsUseCase.Resolution(settings(tenantId = "other"), false)

        val result = fixture.useCase()(receipt)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertFalse(fixture.formatterCalled)
        assertEquals(0, fixture.transportCalls)
    }

    @Test
    fun mismatchedOutletSettingsRejectedBeforeFormattingAndTransport() = runTest {
        val fixture = Fixture()
        val receipt = sampleReceipt()
        fixture.resolution = ResolveReceiptSettingsUseCase.Resolution(settings(outletId = "other"), false)

        val result = fixture.useCase()(receipt)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertFalse(fixture.formatterCalled)
        assertEquals(0, fixture.transportCalls)
    }

    @Test
    fun intentionalLegacyNullPrintsWithCanonicalLegacyRendering() = runTest {
        val fixture = Fixture()
        val receipt = sampleReceipt()
        val context = fixture.useCase()
            .validatedContext(receipt, ResolveReceiptSettingsUseCase.Resolution(null, true), 58)
            .getOrThrow()

        val result = fixture.useCase().invokeResolved(context)

        assertTrue(result.isSuccess)
        assertNull(fixture.lastFormattedSettings)
        assertEquals(58, fixture.lastFormattedWidth)
        assertEquals(0, fixture.resolverCalls.size)
    }

    @Test
    fun resolverFailureFallsBackAccordingToSharedResolverPolicy() = runTest {
        val fixture = Fixture()
        fixture.resolution = ResolveReceiptSettingsUseCase.Resolution(null, true)

        val result = fixture.useCase()(sampleReceipt())

        assertTrue(result.isSuccess)
        assertNull(fixture.lastFormattedSettings)
    }

    @Test
    fun resolverCancellationPropagates() = runTest {
        val fixture = Fixture()
        fixture.resolverError = CancellationException("cancel")

        val thrown = runCatching { fixture.useCase()(sampleReceipt()) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun printerTransportFailureRemainsFailure() = runTest {
        val fixture = Fixture()
        fixture.transportResult = Result.failure(IOException("bluetooth disconnected"))

        val result = fixture.useCase()(sampleReceipt())

        assertTrue(result.isFailure)
        assertEquals("bluetooth disconnected", result.exceptionOrNull()?.message)
        assertEquals(1, fixture.transportCalls)
    }

    @Test
    fun validatedPreviewContextPrintsWithoutAdditionalResolverCall() = runTest {
        val fixture = Fixture()
        val receipt = sampleReceipt()
        val context = fixture.useCase()
            .validatedContext(receipt, ResolveReceiptSettingsUseCase.Resolution(settings(), false), 80)
            .getOrThrow()

        val result = fixture.useCase().invokeResolved(context)

        assertTrue(result.isSuccess)
        assertEquals(0, fixture.resolverCalls.size)
        assertEquals(settings(), fixture.lastFormattedSettings)
    }

    private class Fixture {
        var resolution = ResolveReceiptSettingsUseCase.Resolution(settings(), false)
        var resolverError: Throwable? = null
        var transportResult: Result<Unit> = Result.success(Unit)
        val resolverCalls = mutableListOf<Pair<String, String>>()
        var formatterCalled = false
        var lastFormattedSettings: ReceiptSettings? = null
        var lastFormattedWidth: Int? = null
        var transportCalls = 0

        fun useCase() = PrintReceiptUseCase(
            configProvider = { PrinterConfig(deviceAddress = "printer", paperWidthMm = 80) },
            formatter = { _: Receipt, receiptSettings: ReceiptSettings?, paperWidthMm: Int ->
                formatterCalled = true
                lastFormattedSettings = receiptSettings
                lastFormattedWidth = paperWidthMm
                byteArrayOf(1, 2, 3)
            },
            transport = { _: String, _: ByteArray ->
                transportCalls++
                transportResult
            },
            settingsResolver = { receipt: Receipt ->
                resolverCalls += receipt.tenant.id to receipt.outlet.id
                resolverError?.let { throw it }
                resolution
            },
        )
    }

    private companion object {
        fun sampleReceipt(
            tenantId: String = "tenant",
            outletId: String = "outlet",
        ) = Receipt(
            transactionId = "tx-1",
            clientTransactionId = "client-1",
            status = "completed",
            createdAt = "2026-10-09T00:00:00Z",
            tenant = ReceiptTenant(tenantId, "Tenant", null),
            outlet = ReceiptOutlet(outletId, "Outlet", null),
            cashier = ReceiptCashier("cashier", "Kasir"),
            customer = ReceiptCustomer("cust", "Customer", null, null),
            items = emptyList(),
            payment = Payment("pay", "CASH", "completed", 10_000, "2026-10-09T00:00:00Z", 10_000, 0),
            subtotal = 10_000,
            discount = 0,
            tax = 0,
            total = 10_000,
            change = 0,
        )

        fun settings(
            tenantId: String = "tenant",
            outletId: String = "outlet",
        ) = ReceiptSettings(
            tenantId = tenantId,
            outletId = outletId,
            header = ReceiptHeaderSettings("Store", "Outlet", null, null, null),
            visibility = ReceiptVisibilitySettings(true, true, true, true, true),
            footer = ReceiptFooterSettings("Thanks", null),
            templateVersion = 1,
            createdAt = null,
            updatedAt = null,
        )
    }
}
