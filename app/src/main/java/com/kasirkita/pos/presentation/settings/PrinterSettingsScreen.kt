package com.kasirkita.pos.presentation.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kasirkita.pos.domain.model.PaperWidth
import com.kasirkita.pos.domain.printer.PrinterManager
import kotlinx.coroutines.launch

@Composable
fun PrinterSettingsScreen(
    viewModel: PrinterSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Current printer info
            if (viewModel.hasConfiguredPrinter) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "Printer terpasang: ${viewModel.deviceName ?: "Unknown"}")
                        Text(text = "Alamat: ${viewModel.deviceAddress ?: "unknown"}")

                        // Paper width selection
                        PaperWidth.entries.forEach { width ->
                            Button(
                                onClick = { viewModel.selectPaperWidth(width.mm) },
                                enabled = !viewModel.isLoading,
                            ) {
                                Text("Kertas ${width.mm}mm")
                            }
                        }

                        // Auto-print toggle
                        Button(
                            onClick = { viewModel.toggleAutoPrint() },
                            enabled = !viewModel.isLoading,
                        ) {
                            Text("Cetak Otomatis: ${if (viewModel.autoPrint) "Aktif" else "Nonaktif"}")
                        }

                        // Auto-drawer toggle
                        Button(
                            onClick = { viewModel.toggleAutoDrawer() },
                            enabled = !viewModel.isLoading,
                        ) {
                            Text("Buka Tas Draw Otomatis: ${if (viewModel.autoDrawer) "Aktif" else "Nonaktif"}")
                        }
                    }
                }
            } else {
                Text(text = viewModel.getGuidanceText())
            }

            // Test Printer
            val scope = rememberCoroutineScope()
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
                }
            ) {
                Text("Test Cetak")
            }

            // Test Drawer
            Button(
                enabled = viewModel.hasConfiguredPrinter && !viewModel.isLoading,
                onClick = {
                    scope.launch {
                        val result = viewModel.testDrawer()
                        result.onSuccess {
                            Toast.makeText(context, "Tas draw dibuka", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Gagal membuka tas draw: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) {
                Text("Test Tas Draw")
            }
        }
    }
}
