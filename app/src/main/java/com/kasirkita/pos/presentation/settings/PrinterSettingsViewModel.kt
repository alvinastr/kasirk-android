package com.kasirkita.pos.presentation.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kasirkita.pos.core.permission.BluetoothPermissionHelper
import com.kasirkita.pos.data.datastore.PrinterConfigDataStore
import com.kasirkita.pos.data.printer.BluetoothPrinterTransport
import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.PaperWidth
import com.kasirkita.pos.domain.model.PrinterConfig
import com.kasirkita.pos.domain.model.PrinterError
import com.kasirkita.pos.domain.printer.PrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrinterSettingsViewModel @Inject constructor(
    private val bluetoothPermissionHelper: BluetoothPermissionHelper,
    private val configDataStore: PrinterConfigDataStore,
    private val bluetoothTransport: BluetoothPrinterTransport,
    private val printerManager: PrinterManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

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
    }

    private fun loadConfig() {
        viewModelScope.launch {
            configDataStore.configFlow.collect { config ->
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

    fun requestBluetoothPermission(): Boolean {
        return bluetoothPermissionHelper.hasBluetoothPermissions()
    }

    fun openBluetoothSettings() {
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun selectDevice(device: PrinterManager.PrinterDevice) {
        viewModelScope.launch {
            configDataStore.saveConfig(
                PrinterConfig(
                    deviceAddress = device.address,
                    deviceName = device.name,
                    paperWidthMm = paperWidthMm,
                    autoPrint = autoPrint,
                    autoDrawer = autoDrawer,
                    drawerPulseProfile = drawerPulseProfile,
                )
            )
            deviceAddress = device.address
            deviceName = device.name
        }
    }

    fun toggleAutoPrint() {
        viewModelScope.launch {
            configDataStore.saveConfig(
                PrinterConfig(
                    deviceAddress = deviceAddress,
                    deviceName = deviceName,
                    paperWidthMm = paperWidthMm,
                    autoPrint = !autoPrint,
                    autoDrawer = autoDrawer,
                    drawerPulseProfile = drawerPulseProfile,
                )
            )
            autoPrint = !autoPrint
        }
    }

    fun toggleAutoDrawer() {
        viewModelScope.launch {
            configDataStore.saveConfig(
                PrinterConfig(
                    deviceAddress = deviceAddress,
                    deviceName = deviceName,
                    paperWidthMm = paperWidthMm,
                    autoPrint = autoPrint,
                    autoDrawer = !autoDrawer,
                    drawerPulseProfile = drawerPulseProfile,
                )
            )
            autoDrawer = !autoDrawer
        }
    }

    fun selectPaperWidth(width: Int) {
        viewModelScope.launch {
            configDataStore.saveConfig(
                PrinterConfig(
                    deviceAddress = deviceAddress,
                    deviceName = deviceName,
                    paperWidthMm = width,
                    autoPrint = autoPrint,
                    autoDrawer = autoDrawer,
                    drawerPulseProfile = drawerPulseProfile,
                )
            )
            paperWidthMm = width
        }
    }

    suspend fun testPrinter(): Result<Unit> {
        isLoading = true
        val result = printerManager.testPrinter()
        isLoading = false
        return result
    }

    suspend fun testDrawer(): Result<Unit> {
        isLoading = true
        val result = printerManager.testDrawer()
        isLoading = false
        return result
    }

    fun getGuidanceText(): String {
        return "Pasangkan printer melalui Pengaturan Bluetooth Android terlebih dahulu."
    }
}
