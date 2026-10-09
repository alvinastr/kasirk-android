package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptSettingsUpdate
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import com.kasirkita.pos.domain.repository.ReceiptSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ResolveReceiptSettingsUseCaseTest {

    private val tenantId = "tenant-a"
    private val outletId = "outlet-1"

    @Test
    fun onlineRefreshReturnsSettings() = runTest {
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = tenantId, outletId = outletId)),
        )

        val resolution = ResolveReceiptSettingsUseCase(repository)(receipt())

        assertEquals(settings(tenantId = tenantId, outletId = outletId), resolution.settings)
        assertEquals(false, resolution.usedLegacyFallback)
        assertEquals(listOf(tenantId to outletId), repository.refreshCalls)
    }

    @Test
    fun matchingCacheOnNetworkFailureIsUsed() = runTest {
        val repository = FakeRepository(
            refresh = Result.failure(IOException("offline")),
        )

        val resolution = ResolveReceiptSettingsUseCase(repository)(receipt())

        assertNull(resolution.settings)
        assertTrue(resolution.usedLegacyFallback)
    }

    @Test
    fun repositorySuppliedCacheIsAccepted() = runTest {
        // Repository transparently returns matching cached settings on 5xx.
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = tenantId, outletId = outletId)),
        )

        val resolution = ResolveReceiptSettingsUseCase(repository)(receipt())

        assertEquals(settings(tenantId = tenantId, outletId = outletId), resolution.settings)
        assertEquals(false, resolution.usedLegacyFallback)
    }

    @Test
    fun httpClientErrorsFallBackToLegacy() = runTest {
        listOf(400, 401, 403, 404).forEach { code ->
            val repository = FakeRepository(
                refresh = Result.failure(IllegalStateException("HTTP $code")),
            )

            val resolution = ResolveReceiptSettingsUseCase(repository)(receipt())

            assertNull("HTTP $code must not resolve settings", resolution.settings)
            assertTrue(resolution.usedLegacyFallback)
        }
    }

    @Test
    fun contractMismatchFallsBackToLegacy() = runTest {
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = "other-tenant", outletId = "other-outlet")),
        )

        val resolution = ResolveReceiptSettingsUseCase(repository)(receipt())

        assertNull(resolution.settings)
        assertTrue(resolution.usedLegacyFallback)
    }

    @Test
    fun cancellationPropagates() = runTest {
        val repository = FakeRepository(
            refresh = Result.failure(CancellationException("cancelled")),
        )

        val thrown = runCatching { ResolveReceiptSettingsUseCase(repository)(receipt()) }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun blankTenantIdSkipsLookup() = runTest {
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = "x", outletId = "y")),
        )

        val resolution = ResolveReceiptSettingsUseCase(repository)(
            receipt().copy(tenant = ReceiptTenant("   ", "Tenant", null)),
        )

        assertNull(resolution.settings)
        assertTrue(resolution.usedLegacyFallback)
        assertTrue(repository.refreshCalls.isEmpty())
    }

    @Test
    fun blankOutletIdSkipsLookup() = runTest {
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = "x", outletId = "y")),
        )

        val resolution = ResolveReceiptSettingsUseCase(repository)(
            receipt().copy(outlet = ReceiptOutlet("", "Outlet", null)),
        )

        assertNull(resolution.settings)
        assertTrue(resolution.usedLegacyFallback)
        assertTrue(repository.refreshCalls.isEmpty())
    }

    @Test
    fun usesReceiptIdentityNotAmbientOutlet() = runTest {
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = "other-tenant", outletId = "other-outlet")),
        )

        // Resolver has no outlet parameter at all: only the receipt can decide identity.
        val resolution = ResolveReceiptSettingsUseCase(repository)(
            receipt().copy(
                tenant = ReceiptTenant("receipt-tenant", "T", null),
                outlet = ReceiptOutlet("receipt-outlet", "O", null),
            ),
        )

        assertEquals(listOf("receipt-tenant" to "receipt-outlet"), repository.refreshCalls)
        assertNull(resolution.settings)
        assertTrue(resolution.usedLegacyFallback)
    }

    @Test
    fun resolverNeverWritesSettings() = runTest {
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = tenantId, outletId = outletId)),
        )

        ResolveReceiptSettingsUseCase(repository)(receipt())

        assertTrue(repository.updateCalls.isEmpty())
    }

    @Test
    fun resolverIssuesSingleLookupPerPrintAttempt() = runTest {
        val repository = FakeRepository(
            refresh = Result.success(settings(tenantId = tenantId, outletId = outletId)),
        )
        val resolver = ResolveReceiptSettingsUseCase(repository)

        resolver(receipt())

        assertEquals(1, repository.refreshCalls.size)
    }

    private fun receipt() = Receipt(
        transactionId = "transaction-1",
        clientTransactionId = "client-1",
        status = "completed",
        createdAt = "2026-10-06T00:00:00Z",
        tenant = ReceiptTenant(tenantId, "Tenant", null),
        outlet = ReceiptOutlet(outletId, "Outlet", null),
        cashier = ReceiptCashier("cashier-1", "Kasir"),
        customer = ReceiptCustomer("customer-1", "Pelanggan", null, null),
        items = emptyList(),
        payment = null,
        subtotal = 10_000,
        discount = 0,
        tax = 0,
        total = 10_000,
        change = null,
    )

    private fun settings(tenantId: String, outletId: String) = ReceiptSettings(
        tenantId = tenantId,
        outletId = outletId,
        header = ReceiptHeaderSettings(
            storeName = "Toko",
            outletName = "Outlet",
            address = null,
            phone = null,
            additionalText = null,
        ),
        visibility = ReceiptVisibilitySettings(
            showSku = true,
            showModifiers = true,
            showItemNotes = true,
            showCashier = true,
            showCustomer = true,
        ),
        footer = ReceiptFooterSettings(thankYouText = "Terima kasih", promoText = null),
        templateVersion = 1,
        createdAt = null,
        updatedAt = null,
    )

    private class FakeRepository(
        val refresh: Result<ReceiptSettings>,
    ) : ReceiptSettingsRepository {
        val refreshCalls = mutableListOf<Pair<String, String>>()
        val updateCalls = mutableListOf<Triple<String, String, ReceiptSettingsUpdate>>()

        override suspend fun getCachedSettings(tenantId: String, outletId: String): Result<ReceiptSettings> =
            refresh

        override fun observeCachedSettings(tenantId: String, outletId: String): Flow<ReceiptSettings?> =
            flowOf(refresh.getOrNull())

        override suspend fun refreshSettings(tenantId: String, outletId: String): Result<ReceiptSettings> {
            refreshCalls += tenantId to outletId
            return refresh
        }

        override suspend fun updateSettings(
            tenantId: String,
            outletId: String,
            update: ReceiptSettingsUpdate,
        ): Result<ReceiptSettings> {
            updateCalls += Triple(tenantId, outletId, update)
            return refresh
        }
    }
}