package com.kasirkita.pos.presentation.modifier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.ModifierOption
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ModifierGroupScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModifierViewModel = hiltViewModel()
) {
    val listState by viewModel.listState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ModifierGroup?>(null) }
    var addingOptionForGroup by remember { mutableStateOf<ModifierGroup?>(null) }
    var editingOption by remember { mutableStateOf<Pair<ModifierGroup, ModifierOption>?>(null) }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is ModifierGroupActionState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetActionState()
            }
            is ModifierGroupActionState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Kelola Modifier / Add-on",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Atur grup pilihan tambahan untuk produk.",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Text("Kembali")
                    }
                }

                Button(
                    onClick = { showCreateGroupDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Tambah Grup Modifier")
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (val state = listState) {
                    is ModifierGroupListState.Loading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is ModifierGroupListState.Error -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadModifierGroups() }) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                    is ModifierGroupListState.Success -> {
                        if (state.groups.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Belum ada grup modifier.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(state.groups, key = { it.id }) { group ->
                                    ModifierGroupCard(
                                        group = group,
                                        onEditGroup = { editingGroup = group },
                                        onDeleteGroup = { viewModel.deleteModifierGroup(group.id) },
                                        onAddOption = { addingOptionForGroup = group },
                                        onEditOption = { opt -> editingOption = Pair(group, opt) },
                                        onDeleteOption = { optId -> viewModel.deleteOption(group.id, optId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    if (showCreateGroupDialog) {
        CreateOrEditGroupDialog(
            group = null,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name, displayOrder ->
                viewModel.createModifierGroup(name, displayOrder)
                showCreateGroupDialog = false
            }
        )
    }

    editingGroup?.let { group ->
        CreateOrEditGroupDialog(
            group = group,
            onDismiss = { editingGroup = null },
            onConfirm = { name, displayOrder ->
                viewModel.updateModifierGroup(group.id, name, group.isActive, displayOrder)
                editingGroup = null
            }
        )
    }

    addingOptionForGroup?.let { group ->
        CreateOrEditOptionDialog(
            option = null,
            onDismiss = { addingOptionForGroup = null },
            onConfirm = { name, priceDelta, displayOrder ->
                viewModel.createOption(group.id, name, priceDelta, displayOrder)
                addingOptionForGroup = null
            }
        )
    }

    editingOption?.let { (group, option) ->
        CreateOrEditOptionDialog(
            option = option,
            onDismiss = { editingOption = null },
            onConfirm = { name, priceDelta, displayOrder ->
                viewModel.updateOption(group.id, option.id, name, priceDelta, option.isActive, displayOrder)
                editingOption = null
            }
        )
    }
}

@Composable
fun ModifierGroupCard(
    group: ModifierGroup,
    onEditGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onAddOption: () -> Unit,
    onEditOption: (ModifierOption) -> Unit,
    onDeleteOption: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (group.isActive) "Aktif" else "Nonaktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (group.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEditGroup) {
                        Text("Edit")
                    }
                    TextButton(onClick = onDeleteGroup) {
                        Text("Hapus", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pilihan (${group.options.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onAddOption) {
                    Text("+ Tambah Opsi")
                }
            }

            val formatter = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")) }

            group.options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        val sign = if (option.priceDelta >= 0) "+" else ""
                        Text(
                            text = "$sign Rp${formatter.format(option.priceDelta)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Row {
                        TextButton(onClick = { onEditOption(option) }) {
                            Text("Edit")
                        }
                        TextButton(onClick = { onDeleteOption(option.id) }) {
                            Text("Hapus", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateOrEditGroupDialog(
    group: ModifierGroup?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, displayOrder: Int) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var displayOrder by remember { mutableStateOf((group?.displayOrder ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Tambah Grup Modifier" else "Edit Grup Modifier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Grup") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayOrder,
                    onValueChange = { displayOrder = it },
                    label = { Text("Urutan Tampilan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val order = displayOrder.toIntOrNull() ?: 0
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), order)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun CreateOrEditOptionDialog(
    option: ModifierOption?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, priceDelta: Long, displayOrder: Int) -> Unit
) {
    var name by remember { mutableStateOf(option?.name ?: "") }
    var priceDelta by remember { mutableStateOf((option?.priceDelta ?: 0).toString()) }
    var displayOrder by remember { mutableStateOf((option?.displayOrder ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (option == null) "Tambah Pilihan Modifier" else "Edit Pilihan Modifier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Pilihan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceDelta,
                    onValueChange = { priceDelta = it },
                    label = { Text("Selisih Harga (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayOrder,
                    onValueChange = { displayOrder = it },
                    label = { Text("Urutan Tampilan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceDelta.toLongOrNull() ?: 0L
                    val order = displayOrder.toIntOrNull() ?: 0
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), price, order)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}