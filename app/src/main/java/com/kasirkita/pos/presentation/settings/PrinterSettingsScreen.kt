package com.kasirkita.pos.presentation.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.PaperWidth
import com.kasirkita.pos.domain.printer.PrinterManager
import com.kasirkita.pos.ui.components.KasirTopBar
import kotlinx.coroutines.launch

@Composable
fun PrinterSettingsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: PrinterSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.loadPairedDevices() }

    LaunchedEffect(Unit) {
        viewModel.loadPairedDevices()
    }

    Scaffold(
        topBar = {
            onBack?.let { navigateBack ->
                KasirTopBar(
                    title = "Printer",
                    navigationIcon = {
                        IconButton(onClick = navigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack == null) {
                Text(
                    text = "Printer Settings",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            viewModel.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (viewModel.hasConfiguredPrinter) {
                ConfiguredPrinterCard(viewModel)
            } else {
                UnconfiguredPrinterCard(
                    viewModel = viewModel,
                    onRequestBluetoothPermission = bluetoothPermissionLauncher::launch,
                )
            }

            TestPrinterButton(viewModel, scope, context)
            TestDrawerButton(viewModel, scope, context)
        }
    }
}

@Composable
private fun ConfiguredPrinterCard(viewModel: PrinterSettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Printer terpasang: ${viewModel.deviceName ?: "Unknown"}")
            Text(text = "Alamat: ${viewModel.deviceAddress ?: "unknown"}")

            Text(text = "Lebar kertas")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperWidth.entries.forEach { width ->
                    Button(
                        onClick = { viewModel.selectPaperWidth(width.mm) },
                        enabled = !viewModel.isLoading,
                    ) {
                        Text(
                            text = if (viewModel.paperWidthMm == width.mm) {
                                "${width.mm} mm ✓"
                            } else {
                                "${width.mm} mm"
                            },
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.toggleAutoPrint() },
                enabled = !viewModel.isLoading,
            ) {
                Text("Auto Print: ${if (viewModel.autoPrint) "Aktif" else "Nonaktif"}")
            }

            Button(
                onClick = { viewModel.toggleAutoDrawer() },
                enabled = !viewModel.isLoading,
            ) {
                Text("Auto Drawer: ${if (viewModel.autoDrawer) "Aktif" else "Nonaktif"}")
            }
        }
    }
}

@Composable
private fun UnconfiguredPrinterCard(
    viewModel: PrinterSettingsViewModel,
    onRequestBluetoothPermission: (Array<String>) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = viewModel.getGuidanceText())
            if (viewModel.pairedDevices.isEmpty()) {
                Text(text = "Belum ada printer Bluetooth yang terdeteksi sebagai perangkat terpasang.")
                Button(
                    onClick = {
                        val permissions = viewModel.requiredBluetoothPermissions()
                        if (permissions.isEmpty()) {
                            viewModel.openBluetoothSettings()
                        } else {
                            onRequestBluetoothPermission(permissions)
                        }
                    },
                    enabled = !viewModel.isLoading,
                ) {
                    Text("Buka Bluetooth Settings")
                }
            } else {
                Text(text = "Printer Bluetooth terpasang")
                viewModel.pairedDevices.forEach { device ->
                    PairedDeviceButton(
                        device = device,
                        enabled = !viewModel.isLoading,
                        onSelect = viewModel::selectDevice,
                    )
                }
            }
            Button(
                onClick = viewModel::refreshPairedDevices,
                enabled = !viewModel.isLoading,
            ) {
                Text(if (viewModel.isLoading) "Memuat..." else "Refresh")
            }
        }
    }
}

@Composable
private fun PairedDeviceButton(
    device: PrinterManager.PrinterDevice,
    enabled: Boolean,
    onSelect: (PrinterManager.PrinterDevice) -> Unit,
) {
    Button(
        onClick = { onSelect(device) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(device.name)
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TestPrinterButton(
    viewModel: PrinterSettingsViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
) {
    Button(
        enabled = viewModel.hasConfiguredPrinter && !viewModel.isLoading,
        onClick = {
            scope.launch {
                val result = viewModel.testPrinter()
                result.onSuccess {
                    Toast.makeText(context, "Test cetak berhasil", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Test cetak gagal: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        },
    ) {
        Text("Test Printer")
    }
}

@Composable
private fun TestDrawerButton(
    viewModel: PrinterSettingsViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
) {
    Button(
        enabled = viewModel.hasConfiguredPrinter && !viewModel.isLoading,
        onClick = {
            scope.launch {
                val result = viewModel.testDrawer()
                result.onSuccess {
                    Toast.makeText(context, "Test drawer berhasil", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Gagal membuka drawer: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        },
    ) {
        Text("Test Drawer")
    }
}
