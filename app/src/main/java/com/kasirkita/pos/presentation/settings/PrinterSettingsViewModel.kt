package com.kasirkita.pos.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.core.permission.BluetoothPermissionHelper
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.printer.PrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrinterSettingsViewModel internal constructor(
    private val configFlow: Flow<PrinterConfig>,
    private val saveConfig: suspend (PrinterConfig) -> Unit,
    private val loadPairedDevicesAction: suspend () -> Result<List<PrinterManager.PrinterDevice>>,
    private val hasBluetoothPermissionAction: () -> Boolean,
    private val requiredBluetoothPermissionsAction: () -> Array<String>,
    private val openBluetoothSettingsAction: () -> Unit,
    private val testPrinterAction: suspend () -> Result<Unit>,
    private val testDrawerAction: suspend () -> Result<Unit>,
) : ViewModel() {

    @Inject
    constructor(
        bluetoothPermissionHelper: BluetoothPermissionHelper,
        configDataStore: PrinterConfigDataStore,
        printerManager: PrinterManager,
        @ApplicationContext context: Context,
    ) : this(
        configFlow = configDataStore.configFlow,
        saveConfig = configDataStore::saveConfig,
        loadPairedDevicesAction = printerManager::getPairedDevices,
        hasBluetoothPermissionAction = bluetoothPermissionHelper::hasBluetoothPermissions,
        requiredBluetoothPermissionsAction = bluetoothPermissionHelper::getRequiredPermissions,
        openBluetoothSettingsAction = {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
        testPrinterAction = printerManager::testPrinter,
        testDrawerAction = printerManager::testDrawer,
    )

    var deviceAddress by mutableStateOf<String?>(null)
    var deviceName by mutableStateOf<String?>(null)
    var paperWidthMm by mutableStateOf(58)
    var autoPrint by mutableStateOf(false)
    var autoDrawer by mutableStateOf(false)
    var drawerPulseProfile by mutableStateOf(DrawerPulseProfile.DEFAULT)
    var errorMessage by mutableStateOf<String?>(null)
    var pairedDevices by mutableStateOf<List<PrinterManager.PrinterDevice>>(emptyList())
    var isLoading by mutableStateOf(false)

    init {
        loadConfig()
        loadPairedDevices()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            configFlow.collect { config ->
                deviceAddress = config.deviceAddress
                deviceName = config.deviceName
                paperWidthMm = config.paperWidthMm
                autoPrint = config.autoPrint
                autoDrawer = config.autoDrawer
                drawerPulseProfile = config.drawerPulseProfile
            }
        }
    }

    val hasConfiguredPrinter: Boolean
        get() = deviceAddress != null

    fun requestBluetoothPermission(): Boolean = hasBluetoothPermissionAction()

    fun requiredBluetoothPermissions(): Array<String> = requiredBluetoothPermissionsAction()

    fun loadPairedDevices() {
        if (!requestBluetoothPermission()) {
            pairedDevices = emptyList()
            errorMessage = "Izin Bluetooth diperlukan untuk membaca printer yang sudah dipasangkan."
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            loadPairedDevicesAction().fold(
                onSuccess = { devices ->
                    pairedDevices = devices
                    errorMessage = null
                },
                onFailure = {
                    pairedDevices = emptyList()
                    errorMessage = "Perangkat Bluetooth tidak dapat dimuat. Coba lagi."
                },
            )
            isLoading = false
        }
    }

    fun refreshPairedDevices() = loadPairedDevices()

    fun openBluetoothSettings() {
        openBluetoothSettingsAction()
    }

    fun selectDevice(device: PrinterManager.PrinterDevice) {
        viewModelScope.launch {
            val config = currentConfig().copy(
                deviceAddress = device.address,
                deviceName = device.name,
            )
            saveConfig(config)
            deviceAddress = device.address
            deviceName = device.name
            errorMessage = null
        }
    }

    fun toggleAutoPrint() {
        viewModelScope.launch {
            val config = currentConfig().copy(autoPrint = !autoPrint)
            saveConfig(config)
            autoPrint = config.autoPrint
        }
    }

    fun toggleAutoDrawer() {
        viewModelScope.launch {
            val config = currentConfig().copy(autoDrawer = !autoDrawer)
            saveConfig(config)
            autoDrawer = config.autoDrawer
        }
    }

    fun selectPaperWidth(width: Int) {
        viewModelScope.launch {
            val config = currentConfig().copy(paperWidthMm = width)
            saveConfig(config)
            paperWidthMm = width
        }
    }

    suspend fun testPrinter(): Result<Unit> {
        isLoading = true
        val result = testPrinterAction()
        isLoading = false
        return result
    }

    suspend fun testDrawer(): Result<Unit> {
        isLoading = true
        val result = testDrawerAction()
        isLoading = false
        return result
    }

    fun getGuidanceText(): String {
        return "Pilih printer Bluetooth yang sudah dipasangkan di Android Settings."
    }

    private fun currentConfig(): PrinterConfig = PrinterConfig(
        deviceAddress = deviceAddress,
        deviceName = deviceName,
        paperWidthMm = paperWidthMm,
        autoPrint = autoPrint,
        autoDrawer = autoDrawer,
        drawerPulseProfile = drawerPulseProfile,
    )
}
