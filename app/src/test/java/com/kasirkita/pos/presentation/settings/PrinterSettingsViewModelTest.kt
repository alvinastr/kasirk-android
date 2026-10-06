package com.kasirkita.pos.presentation.settings

import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.printer.PrinterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val configFlow = MutableStateFlow(PrinterConfig())
    private val savedConfigs = mutableListOf<PrinterConfig>()
    private var pairedDevicesResult: Result<List<PrinterManager.PrinterDevice>> = Result.success(
        listOf(PrinterManager.PrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "RPP02N")),
    )
    private var permissionGranted = true
    private var settingsOpened = false
    private var requiredPermissions: Array<String> = emptyArray()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsBondedDevicesWhenOpened() = runTest(dispatcher) {
        val viewModel = viewModel()

        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertEquals(
            listOf(PrinterManager.PrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "RPP02N")),
            viewModel.pairedDevices,
        )
        assertEquals(null, viewModel.errorMessage)
    }

    @Test
    fun loadFailureSurfacesUsefulState() = runTest(dispatcher) {
        pairedDevicesResult = Result.failure(IOException("bluetooth unavailable"))
        val viewModel = viewModel()

        advanceUntilIdle()

        assertTrue(viewModel.pairedDevices.isEmpty())
        assertEquals("Perangkat Bluetooth tidak dapat dimuat. Coba lagi.", viewModel.errorMessage)
    }

    @Test
    fun missingPermissionSurfacesGuidanceAndCanOpenBluetoothSettings() = runTest(dispatcher) {
        permissionGranted = false
        val viewModel = viewModel()

        advanceUntilIdle()
        viewModel.openBluetoothSettings()

        assertTrue(viewModel.pairedDevices.isEmpty())
        assertEquals("Izin Bluetooth diperlukan untuk membaca printer yang sudah dipasangkan.", viewModel.errorMessage)
        assertTrue(settingsOpened)
    }

    @Test
    fun selectingDevicePersistsAddressNameAndConfiguredState() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.selectDevice(PrinterManager.PrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "RPP02N"))
        advanceUntilIdle()

        assertEquals("AA:BB:CC:DD:EE:FF", savedConfigs.single().deviceAddress)
        assertEquals("RPP02N", savedConfigs.single().deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", viewModel.deviceAddress)
        assertEquals("RPP02N", viewModel.deviceName)
        assertTrue(viewModel.hasConfiguredPrinter)
    }

    private fun viewModel(): PrinterSettingsViewModel = PrinterSettingsViewModel(
        configFlow = configFlow,
        saveConfig = { config ->
            savedConfigs += config
            configFlow.value = config
        },
        loadPairedDevicesAction = { pairedDevicesResult },
        hasBluetoothPermissionAction = { permissionGranted },
        requiredBluetoothPermissionsAction = { requiredPermissions },
        openBluetoothSettingsAction = { settingsOpened = true },
        testPrinterAction = { Result.success(Unit) },
        testDrawerAction = { Result.success(Unit) },
    )
}
