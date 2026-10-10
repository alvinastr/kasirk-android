package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class PrintAfterCheckoutUseCaseTest {

    @Test
    fun autoPrintAppliesSettings() = runTest {
        val fixture = Fixture(config = printerConfig(paperWidthMm = 80))
        val settings = settings("AutoPrint Store")
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(settings, false))

        val result = useCase(receipt = sampleReceipt(), paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertTrue(result is PrintAfterCheckoutUseCase.Result.Success)
        assertTrue((result as PrintAfterCheckoutUseCase.Result.Success).printed)
        assertEquals(settings, fixture.lastSettings)
        assertEquals(80, fixture.lastPaperWidthMm)
        assertEquals(1, fixture.settingsResolverCalls)
    }

    @Test
    fun autoPrintOfflineCacheApplied() = runTest {
        val fixture = Fixture(config = printerConfig(paperWidthMm = 58))
        val cachedSettings = settings("Cached Store")
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(cachedSettings, false))

        val result = useCase(receipt = sampleReceipt(), paymentMethod = "QRIS", isOriginalOnlineCheckout = true)

        assertTrue(result is PrintAfterCheckoutUseCase.Result.Success)
        assertEquals(cachedSettings, fixture.lastSettings)
        assertEquals(58, fixture.lastPaperWidthMm)
    }

    @Test
    fun autoPrintNoCacheUsesLegacyFallback() = runTest {
        val fixture = Fixture(config = printerConfig())
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(null, true))

        val result = useCase(receipt = sampleReceipt(), paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertTrue(result is PrintAfterCheckoutUseCase.Result.Success)
        assertNull(fixture.lastSettings)
    }

    @Test
    fun autoPrintSettingsFailureDoesNotFailTransaction() = runTest {
        val fixture = Fixture(config = printerConfig())
        val useCase = fixture.useCase(settingsException = IOException("network down"))

        val result = useCase(receipt = sampleReceipt(), paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertTrue(result is PrintAfterCheckoutUseCase.Result.Success)
        assertTrue((result as PrintAfterCheckoutUseCase.Result.Success).printed)
        assertNull(fixture.lastSettings)
    }

    @Test
    fun autoPrintPrinterFailureDoesNotRollbackTransaction() = runTest {
        val fixture = Fixture(config = printerConfig(), printException = IOException("printer offline"))
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(null, true))

        val result = useCase(receipt = sampleReceipt(), paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertTrue(result is PrintAfterCheckoutUseCase.Result.Failure)
        val failure = result as PrintAfterCheckoutUseCase.Result.Failure
        assertTrue(failure.transactionSuccessful)
        assertNotNull(failure.printError)
    }

    @Test
    fun autoPrintIssuesSingleSettingsRequest() = runTest {
        val fixture = Fixture(config = printerConfig(autoDrawer = true))
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(null, true))

        useCase(receipt = sampleReceipt(), paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertEquals(1, fixture.settingsResolverCalls)
    }

    @Test
    fun drawerBehaviorAndOrderingUnchanged() = runTest {
        val fixture = Fixture(config = printerConfig(autoDrawer = true))
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(null, true))

        val result = useCase(receipt = sampleReceipt(), paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertTrue(result is PrintAfterCheckoutUseCase.Result.Success)
        assertEquals(listOf("receipt", "drawer"), fixture.printedKinds)
    }

    @Test
    fun autoPrintDisabledSkipsPrintAndSettingsLookup() = runTest {
        val fixture = Fixture(config = printerConfig(autoPrint = false, autoDrawer = false))
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(settings("unused"), false))

        val result = useCase(receipt = sampleReceipt(), paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertTrue(result is PrintAfterCheckoutUseCase.Result.Success)
        assertEquals(false, (result as PrintAfterCheckoutUseCase.Result.Success).printed)
        assertEquals(0, fixture.settingsResolverCalls)
        assertTrue(fixture.printedKinds.isEmpty())
    }

    @Test
    fun cancellationFromSettingsResolutionPropagates() = runTest {
        val fixture = Fixture(config = printerConfig())
        val useCase = fixture.useCase(settingsException = CancellationException("cancel settings"))

        val thrown = runCatching { useCase(sampleReceipt(), "CASH", true) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun cancellationFromReceiptTransportPropagates() = runTest {
        val fixture = Fixture(config = printerConfig(), printException = CancellationException("cancel receipt"))
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(null, true))

        val thrown = runCatching { useCase(sampleReceipt(), "CASH", true) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun cancellationFromDrawerTransportPropagates() = runTest {
        val fixture = Fixture(
            config = printerConfig(autoDrawer = true),
            transportFailures = ArrayDeque(listOf(null, CancellationException("cancel drawer"))),
        )
        val useCase = fixture.useCase(settingsResolution = ResolveReceiptSettingsUseCase.Resolution(null, true))

        val thrown = runCatching { useCase(sampleReceipt(), "CASH", true) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(listOf("receipt"), fixture.printedKinds)
    }

    private class Fixture(
        private val config: PrinterConfig,
        private val printException: Throwable? = null,
        private val transportFailures: ArrayDeque<Throwable?> = ArrayDeque(),
    ) {
        var settingsResolverCalls = 0
        var lastSettings: ReceiptSettings? = null
        var lastPaperWidthMm: Int? = null
        var lastDocument: ReceiptDocument? = null
        val printedKinds = mutableListOf<String>()

        fun useCase(
            settingsResolution: ResolveReceiptSettingsUseCase.Resolution? = null,
            settingsException: Throwable? = null,
        ) = PrintAfterCheckoutUseCase(
            configProvider = { config },
            documentBuilder = { receipt: Receipt, settings: ReceiptSettings?, paperWidthMm: Int ->
                lastSettings = settings
                lastPaperWidthMm = paperWidthMm
                BuildReceiptDocumentUseCase()(receipt, settings, paperWidthMm)
            },
            receiptFormatter = { document: ReceiptDocument ->
                lastDocument = document
                byteArrayOf(0x1b, 0x40)
            },
            drawerFormatter = { byteArrayOf(0x1b, 0x70) },
            transport = { _: String, data: ByteArray ->
                val error = if (transportFailures.isNotEmpty()) transportFailures.removeFirst() else printException
                if (error != null) {
                    Result.failure(error)
                } else {
                    printedKinds += if (data.contentEquals(byteArrayOf(0x1b, 0x70))) "drawer" else "receipt"
                    Result.success(Unit)
                }
            },
            settingsResolver = { _: Receipt ->
                settingsResolverCalls++
                if (settingsException != null) throw settingsException
                settingsResolution ?: ResolveReceiptSettingsUseCase.Resolution(null, true)
            },
            drawerEligible = { _: Boolean, _: String, _: Boolean, enabled: Boolean -> enabled },
        )
    }

    private fun printerConfig(
        paperWidthMm: Int = 58,
        autoPrint: Boolean = true,
        autoDrawer: Boolean = false,
    ) = PrinterConfig(
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        deviceName = "Test Printer",
        paperWidthMm = paperWidthMm,
        autoPrint = autoPrint,
        autoDrawer = autoDrawer,
        drawerPulseProfile = DrawerPulseProfile.DEFAULT,
    )

    private fun sampleReceipt() = Receipt(
        transactionId = "tx-auto",
        clientTransactionId = "client-auto",
        status = "completed",
        createdAt = "2026-10-06T00:00:00Z",
        tenant = ReceiptTenant("tenant", "Tenant", null),
        outlet = ReceiptOutlet("outlet", "Outlet", null),
        cashier = ReceiptCashier("cashier", "Kasir"),
        customer = ReceiptCustomer("cust-1", "Pelanggan", null, null),
        items = emptyList(),
        payment = Payment("pay-1", "CASH", "completed", 10_000, "2026-10-06T00:00:00Z", 20_000, 10_000),
        subtotal = 10_000,
        discount = 0,
        tax = 0,
        total = 10_000,
        change = 10_000,
    )

    private fun settings(storeName: String) = ReceiptSettings(
        tenantId = "tenant",
        outletId = "outlet",
        header = ReceiptHeaderSettings(storeName = storeName, outletName = "Outlet", address = null, phone = null, additionalText = null),
        visibility = ReceiptVisibilitySettings(showSku = true, showModifiers = true, showItemNotes = true, showCashier = true, showCustomer = true),
        footer = ReceiptFooterSettings(thankYouText = "Thanks", promoText = null),
        templateVersion = 1,
        createdAt = null,
        updatedAt = null,
    )
}
