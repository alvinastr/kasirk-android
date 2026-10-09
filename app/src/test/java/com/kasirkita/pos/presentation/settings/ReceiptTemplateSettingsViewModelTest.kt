package com.kasirkita.pos.presentation.settings

import com.kasirkita.pos.domain.error.ReceiptSettingsError
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptSettingsUpdate
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import com.kasirkita.pos.domain.model.UserRole
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptTemplateSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val refreshCalls = mutableListOf<Pair<String, String>>()
    private val updateCalls = mutableListOf<Triple<String, String, ReceiptSettingsUpdate>>()
    private var refreshResult: Result<ReceiptSettings> = Result.success(settings())
    private var updateResult: Result<ReceiptSettings> = Result.success(settings())

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun ownerLoadUsesExactContext() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(ReceiptSettingsContext("tenant-1", "outlet-1", UserRole.OWNER))
        advanceUntilIdle()
        assertEquals(listOf("tenant-1" to "outlet-1"), refreshCalls)
        assertTrue(vm.state.value is ReceiptTemplateSettingsState.Editing)
    }

    @Test fun adminCanLoad() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(ReceiptSettingsContext("tenant-1", "outlet-1", UserRole.ADMIN))
        advanceUntilIdle()
        assertTrue(vm.state.value is ReceiptTemplateSettingsState.Editing)
    }

    @Test fun cashierDeniedWithoutRepositoryAccess() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(ReceiptSettingsContext("tenant-1", "outlet-1", UserRole.CASHIER))
        advanceUntilIdle()
        assertTrue(vm.state.value is ReceiptTemplateSettingsState.AccessDenied)
        assertTrue(refreshCalls.isEmpty())
    }

    @Test fun missingOutletDoesNotRequest() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(ReceiptSettingsContext("tenant-1", null, UserRole.OWNER))
        advanceUntilIdle()
        assertTrue(vm.state.value is ReceiptTemplateSettingsState.MissingOutlet)
        assertTrue(refreshCalls.isEmpty())
    }

    @Test fun duplicateLoadIsPrevented() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<ReceiptSettings>>()
        val vm = viewModel(refresh = { tenant, outlet -> refreshCalls += tenant to outlet; gate.await() })
        val context = ReceiptSettingsContext("tenant-1", "outlet-1", UserRole.OWNER)
        vm.load(context); runCurrent(); vm.retry(); runCurrent()
        assertEquals(1, refreshCalls.size)
        gate.complete(Result.success(settings()))
    }

    @Test fun knownErrorsMapToSpecificRecoverableMessages() = runTest(dispatcher) {
        val expected = mapOf(
            400 to "Data template struk tidak valid.",
            401 to "Sesi berakhir. Silakan masuk kembali.",
            403 to "Anda tidak memiliki akses ke template struk.",
            404 to "Pengaturan template struk tidak ditemukan.",
        )
        expected.forEach { (code, message) ->
            refreshResult = Result.failure(ReceiptSettingsError(code, "TEST", "server"))
            val vm = viewModel()
            vm.load(ReceiptSettingsContext("tenant-1", "outlet-1", UserRole.OWNER))
            advanceUntilIdle()
            assertEquals(message, (vm.state.value as ReceiptTemplateSettingsState.Error).message)
        }
    }

    @Test fun everyFieldUpdatesAndDirtyStateTracksCanonicalValues() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.updateField(ReceiptSettingsField.StoreName, " New Store ")
        vm.updateField(ReceiptSettingsField.OutletName, "New Outlet")
        vm.updateField(ReceiptSettingsField.Address, " ")
        vm.updateField(ReceiptSettingsField.Phone, "0812")
        vm.updateField(ReceiptSettingsField.AdditionalHeaderText, "Header")
        vm.updateField(ReceiptSettingsField.ThankYouText, "Thanks")
        vm.updateField(ReceiptSettingsField.PromotionalFooterText, "Promo")
        ReceiptToggle.entries.forEach { vm.updateToggle(it, false) }
        val editing = vm.state.value as ReceiptTemplateSettingsState.Editing
        assertTrue(editing.isDirty)
        assertEquals(" New Store ", editing.form.storeName)
        assertEquals("Promo", editing.form.promotionalFooterText)
    }

    @Test fun requiredAndMaximumLengthValidationDisablesSave() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.updateField(ReceiptSettingsField.StoreName, "   ")
        assertEquals("Wajib diisi.", editing(vm).errors.storeName)
        assertFalse(editing(vm).canSave)
        vm.updateField(ReceiptSettingsField.StoreName, "x".repeat(101))
        assertEquals("Maksimal 100 karakter.", editing(vm).errors.storeName)
        vm.updateField(ReceiptSettingsField.Address, "x".repeat(501))
        assertEquals("Maksimal 500 karakter.", editing(vm).errors.address)
    }

    @Test fun unchangedFormCannotSave() = runTest(dispatcher) {
        assertFalse(editing(loadedViewModel()).canSave)
    }

    @Test fun nullableBlankValuesBecomeExplicitNullAndAllFieldsAreSent() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.updateField(ReceiptSettingsField.Address, "  ")
        vm.updateField(ReceiptSettingsField.Phone, "")
        vm.updateField(ReceiptSettingsField.AdditionalHeaderText, " ")
        vm.updateField(ReceiptSettingsField.PromotionalFooterText, "  ")
        vm.save(); advanceUntilIdle()
        val sent = updateCalls.single().third
        assertNull(sent.headerAddress); assertNull(sent.headerPhone)
        assertNull(sent.headerAdditionalText); assertNull(sent.footerPromoText)
        assertEquals("Store", sent.headerStoreName)
        assertEquals("Outlet", sent.headerOutletName)
        assertEquals("Thanks", sent.footerThankYouText)
    }

    @Test fun successfulSaveAdoptsCanonicalServerResponse() = runTest(dispatcher) {
        updateResult = Result.success(settings(storeName = "Canonical"))
        val vm = loadedViewModel()
        vm.updateField(ReceiptSettingsField.StoreName, "Client")
        vm.save(); advanceUntilIdle()
        assertEquals("Canonical", editing(vm).form.storeName)
        assertFalse(editing(vm).isDirty)
        assertTrue(editing(vm).savedConfirmation)
    }

    @Test fun failedSavePreservesEdits() = runTest(dispatcher) {
        updateResult = Result.failure(ReceiptSettingsError(500, "SERVER", "fail"))
        val vm = loadedViewModel()
        vm.updateField(ReceiptSettingsField.StoreName, "Unsaved")
        vm.save(); advanceUntilIdle()
        assertEquals("Unsaved", editing(vm).form.storeName)
        assertTrue(editing(vm).isDirty)
        assertEquals("Gagal menyimpan template struk. Coba lagi.", editing(vm).saveError)
    }

    @Test fun outletContextChangeCannotSaveStaleForm() = runTest(dispatcher) {
        val vm = loadedViewModel()
        vm.updateField(ReceiptSettingsField.StoreName, "Changed")
        vm.updateContext(ReceiptSettingsContext("tenant-1", "outlet-2", UserRole.OWNER))
        vm.save(); advanceUntilIdle()
        assertTrue(updateCalls.isEmpty())
        assertEquals("tenant-1" to "outlet-2", refreshCalls.last())
        assertTrue(vm.state.value is ReceiptTemplateSettingsState.Editing)
    }

    @Test fun previewUsesCanonicalWidthsAndInvalidInputDoesNotCrash() = runTest(dispatcher) {
        val vm = loadedViewModel()
        assertEquals(32, editing(vm).preview.characterWidth)
        vm.selectPaperWidth(80)
        assertEquals(48, editing(vm).preview.characterWidth)
        vm.updateField(ReceiptSettingsField.StoreName, "\u001B" + "x".repeat(101))
        assertTrue(editing(vm).preview.lines.none { line -> line.text.contains('\u001B') })
    }

    private fun loadedViewModel(): ReceiptTemplateSettingsViewModel {
        val vm = viewModel()
        vm.load(ReceiptSettingsContext("tenant-1", "outlet-1", UserRole.OWNER))
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun editing(vm: ReceiptTemplateSettingsViewModel) =
        vm.state.value as ReceiptTemplateSettingsState.Editing

    private fun viewModel(
        refresh: suspend (String, String) -> Result<ReceiptSettings> = { tenant, outlet ->
            refreshCalls += tenant to outlet; refreshResult
        },
    ) = ReceiptTemplateSettingsViewModel(
        refreshSettings = refresh,
        updateSettings = { tenant, outlet, update ->
            updateCalls += Triple(tenant, outlet, update); updateResult
        },
    )

    private companion object {
        fun settings(storeName: String = "Store") = ReceiptSettings(
            tenantId = "tenant-1", outletId = "outlet-1",
            header = ReceiptHeaderSettings(storeName, "Outlet", "Address", "Phone", "Header"),
            visibility = ReceiptVisibilitySettings(true, true, true, true, true),
            footer = ReceiptFooterSettings("Thanks", "Promo"),
            templateVersion = 1, createdAt = null, updatedAt = null,
        )
    }
}
