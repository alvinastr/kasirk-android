package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.data.printer.EscPosReceiptFormatter
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReceiptPrintParityTest {
    private val builder = BuildReceiptDocumentUseCase()
    private val formatter = EscPosReceiptFormatter()

    @Test
    fun manualAutoPrintAndReprintEncodeEquivalentCanonicalContent() = runTest {
        val receipt = receipt()
        val settings = settings()
        val resolution = ResolveReceiptSettingsUseCase.Resolution(settings, false)
        val previewDocument = builder(receipt, settings, 58)
        var manualFormatted: ReceiptDocument? = null
        var reprintFormatted: ReceiptDocument? = null
        var autoFormatted: ReceiptDocument? = null
        var manualBytes: ByteArray? = null
        var reprintBytes: ByteArray? = null
        var autoBytes: ByteArray? = null

        val manualPrinter = printUseCase(
            resolution = resolution,
            onFormat = { manualFormatted = it },
            onTransport = { manualBytes = it },
        )
        val manualContext = manualPrinter.validatedContext(receipt, resolution, previewDocument).getOrThrow()
        manualPrinter.invokeResolved(manualContext).getOrThrow()

        printUseCase(
            resolution = resolution,
            onFormat = { reprintFormatted = it },
            onTransport = { reprintBytes = it },
        )(receipt).getOrThrow()

        PrintAfterCheckoutUseCase(
            configProvider = { printerConfig() },
            documentBuilder = builder::invoke,
            receiptFormatter = { document ->
                autoFormatted = document
                formatter.formatReceipt(document)
            },
            drawerFormatter = formatter::formatDrawerPulse,
            transport = { _, bytes ->
                autoBytes = bytes
                Result.success(Unit)
            },
            settingsResolver = { resolution },
            drawerEligible = { _, _, _, _ -> false },
        )(receipt, paymentMethod = "CASH", isOriginalOnlineCheckout = true)

        assertSame(previewDocument, manualFormatted)
        assertEquals(previewDocument, reprintFormatted)
        assertEquals(previewDocument, autoFormatted)
        assertArrayEquals(manualBytes, reprintBytes)
        assertArrayEquals(manualBytes, autoBytes)
    }

    private fun printUseCase(
        resolution: ResolveReceiptSettingsUseCase.Resolution,
        onFormat: (ReceiptDocument) -> Unit,
        onTransport: (ByteArray) -> Unit,
    ) = PrintReceiptUseCase(
        configProvider = { printerConfig() },
        documentBuilder = builder::invoke,
        formatter = { document ->
            onFormat(document)
            formatter.formatReceipt(document)
        },
        transport = { _, bytes ->
            onTransport(bytes)
            Result.success(Unit)
        },
        settingsResolver = { resolution },
    )

    private fun printerConfig() = PrinterConfig(
        deviceAddress = "printer",
        paperWidthMm = 58,
        autoPrint = true,
        autoDrawer = false,
        drawerPulseProfile = DrawerPulseProfile.DEFAULT,
    )

    private fun receipt() = Receipt(
        transactionId = "tx-parity",
        clientTransactionId = "client-parity",
        status = "completed",
        createdAt = "2026-10-09T00:00:00Z",
        tenant = ReceiptTenant("tenant", "Tenant", null),
        outlet = ReceiptOutlet("outlet", "Outlet", null),
        cashier = ReceiptCashier("cashier", "Kasir"),
        customer = ReceiptCustomer("customer", "Pelanggan", null, null),
        items = emptyList(),
        payment = Payment("payment", "CASH", "completed", 10_000, null, 10_000, 0),
        subtotal = 10_000,
        discount = 0,
        tax = 0,
        total = 10_000,
        change = 0,
    )

    private fun settings() = ReceiptSettings(
        tenantId = "tenant",
        outletId = "outlet",
        header = ReceiptHeaderSettings(
            storeName = "Coffee & Shop Toga",
            outletName = "Outlet",
            address = "Jl. Perjuangan No.4",
            phone = null,
            additionalText = "Instagram : kopitoga_bekasi\nWifi Password: arabicatoraja",
        ),
        visibility = ReceiptVisibilitySettings(true, true, true, true, true),
        footer = ReceiptFooterSettings("Terima kasih", "Promo akhir pekan"),
        templateVersion = 1,
        createdAt = null,
        updatedAt = null,
    )
}
