package com.kasirkita.pos.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.Transaction
import com.kasirkita.pos.domain.repository.ReceiptRepository
import com.kasirkita.pos.domain.repository.TransactionRepository
import com.kasirkita.pos.domain.usecase.GetReceiptUseCase
import com.kasirkita.pos.domain.usecase.GetTransactionDetailUseCase
import com.kasirkita.pos.domain.usecase.PrintReceiptUseCase
import com.kasirkita.pos.domain.usecase.ResolveReceiptSettingsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransactionDetailReprintIsolationTest {

    @Test
    fun reprintUsesStoredOutletIdentityNotCurrentSelection() = runTest {
        // receipt belongs to tenant-old / outlet-old
        val receipt = Receipt(
            transactionId = "tx-reprint",
            clientTransactionId = "client-reprint",
            status = "completed",
            createdAt = "2026-10-09T00:00:00Z",
            tenant = ReceiptTenant("tenant-old", "Old Tenant", null),
            outlet = ReceiptOutlet("outlet-old", "Old Outlet", null),
            cashier = ReceiptCashier("cashier", "Kasir"),
            customer = ReceiptCustomer("cust", "Customer", null, null),
            items = emptyList(),
            payment = null,
            subtotal = 10_000,
            discount = 0,
            tax = 0,
            total = 10_000,
            change = 0,
        )

        // Simulate current user is viewing a different outlet context
        val currentTenant = "tenant-current"
        val currentOutlet = "outlet-current"

        val capturedResolverArgs = mutableListOf<Pair<String, String>>()
        val capturedTransport = mutableListOf<ByteArray>()

        val printReceipt = PrintReceiptUseCase(
            configProvider = { com.kasirkita.pos.domain.model.PrinterConfig(deviceAddress = "printer", paperWidthMm = 58) },
            formatter = { _: Receipt, _, _: Int -> byteArrayOf(0x1b, 0x40) },
            transport = { _: String, data: ByteArray ->
                capturedTransport += data
                Result.success(Unit)
            },
            settingsResolver = { r: Receipt ->
                capturedResolverArgs += r.tenant.id to r.outlet.id
                ResolveReceiptSettingsUseCase.Resolution(null, true)
            },
        )

        val result = printReceipt(receipt)

        assertTrue(result.isSuccess)
        assertEquals(1, capturedResolverArgs.size)
        assertEquals("tenant-old" to "outlet-old", capturedResolverArgs[0])
        assertEquals(1, capturedTransport.size)
        // ensure no lookup for current outlet
        assertTrue(capturedResolverArgs.none { it.first == currentTenant && it.second == currentOutlet })
    }

    @Test
    fun reprintResolvesSettingsExactlyOnce() = runTest {
        val receipt = Receipt(
            transactionId = "tx-reprint",
            clientTransactionId = "client-reprint",
            status = "completed",
            createdAt = "2026-10-09T00:00:00Z",
            tenant = ReceiptTenant("tenant-old", "Old Tenant", null),
            outlet = ReceiptOutlet("outlet-old", "Old Outlet", null),
            cashier = ReceiptCashier("cashier", "Kasir"),
            customer = ReceiptCustomer("cust", "Customer", null, null),
            items = emptyList(),
            payment = null,
            subtotal = 10_000,
            discount = 0,
            tax = 0,
            total = 10_000,
            change = 0,
        )

        var resolverCallCount = 0
        val printReceipt = PrintReceiptUseCase(
            configProvider = { com.kasirkita.pos.domain.model.PrinterConfig(deviceAddress = "printer", paperWidthMm = 58) },
            formatter = { _: Receipt, _, _: Int -> byteArrayOf(0x1b, 0x40) },
            transport = { _: String, _: ByteArray -> Result.success(Unit) },
            settingsResolver = { _: Receipt ->
                resolverCallCount++
                ResolveReceiptSettingsUseCase.Resolution(null, true)
            },
        )

        printReceipt(receipt)
        printReceipt(receipt)

        assertEquals(2, resolverCallCount) // each independent reprint resolves
    }

    @Test
    fun validatedPreviewContextPrintsWithoutAdditionalResolverCall() = runTest {
        val receipt = Receipt(
            transactionId = "tx-reprint",
            clientTransactionId = "client-reprint",
            status = "completed",
            createdAt = "2026-10-09T00:00:00Z",
            tenant = ReceiptTenant("tenant-old", "Old Tenant", null),
            outlet = ReceiptOutlet("outlet-old", "Old Outlet", null),
            cashier = ReceiptCashier("cashier", "Kasir"),
            customer = ReceiptCustomer("cust", "Customer", null, null),
            items = emptyList(),
            payment = null,
            subtotal = 10_000,
            discount = 0,
            tax = 0,
            total = 10_000,
            change = 0,
        )

        var resolverCallCount = 0
        val printReceipt = PrintReceiptUseCase(
            configProvider = { com.kasirkita.pos.domain.model.PrinterConfig(deviceAddress = "printer", paperWidthMm = 58) },
            formatter = { _: Receipt, _, _: Int -> byteArrayOf(0x1b, 0x40) },
            transport = { _: String, _: ByteArray -> Result.success(Unit) },
            settingsResolver = { _: Receipt ->
                resolverCallCount++
                ResolveReceiptSettingsUseCase.Resolution(null, true)
            },
        )

        // preview produces validated context directly (no resolver call happens here)
        val context = printReceipt
            .validatedContext(receipt, ResolveReceiptSettingsUseCase.Resolution(null, true), 58)
            .getOrThrow()

        // manual print uses the same context
        printReceipt.invokeResolved(context)
        printReceipt.invokeResolved(context)

        assertEquals(0, resolverCallCount) // validated context never re-resolves
    }
}