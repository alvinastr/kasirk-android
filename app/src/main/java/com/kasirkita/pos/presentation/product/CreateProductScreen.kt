package com.kasirkita.pos.presentation.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CreateProductScreen(
    onCancel: () -> Unit,
    onProductCreated: () -> Unit,
    viewModel: ProductManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isLoading = state is ProductManagementState.Loading

    var name by rememberSaveable { mutableStateOf("") }
    var sku by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var minimumStock by rememberSaveable { mutableStateOf("") }
    var usesCategoryId by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var trackStock by rememberSaveable { mutableStateOf(true) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var fieldErrors by remember { mutableStateOf(CreateProductFormErrors()) }

    LaunchedEffect(state) {
        if (state is ProductManagementState.Success) {
            onProductCreated()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Tambah Produk",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Isi data utama produk, lalu simpan.",
                style = MaterialTheme.typography.bodyMedium,
            )
    
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    fieldErrors = fieldErrors.copy(name = null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                isError = fieldErrors.name != null,
                label = { Text("Nama Produk") },
                supportingText = fieldErrors.name?.let { message ->
                    { Text(message) }
                },
                singleLine = true,
            )
    
            OutlinedTextField(
                value = sku,
                onValueChange = {
                    sku = it
                    fieldErrors = fieldErrors.copy(sku = null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                isError = fieldErrors.sku != null,
                label = { Text("SKU") },
                supportingText = fieldErrors.sku?.let { message ->
                    { Text(message) }
                },
                singleLine = true,
            )
    
            OutlinedTextField(
                value = price,
                onValueChange = {
                    price = it
                    fieldErrors = fieldErrors.copy(price = null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                isError = fieldErrors.price != null,
                label = { Text("Harga Jual (Rp)") },
                supportingText = fieldErrors.price?.let { message ->
                    { Text(message) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
    
            OutlinedTextField(
                value = cost,
                onValueChange = {
                    cost = it
                    fieldErrors = fieldErrors.copy(cost = null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                isError = fieldErrors.cost != null,
                label = { Text("Harga Modal (Rp, opsional)") },
                supportingText = fieldErrors.cost?.let { message ->
                    { Text(message) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
    
            OutlinedTextField(
                value = minimumStock,
                onValueChange = {
                    minimumStock = it
                    fieldErrors = fieldErrors.copy(minimumStock = null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                isError = fieldErrors.minimumStock != null,
                label = { Text("Stok Minimum (opsional)") },
                supportingText = fieldErrors.minimumStock?.let { message ->
                    { Text(message) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
    
            Text(
                text = "Kategori",
                style = MaterialTheme.typography.labelLarge,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { categoryMenuExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    enabled = !isLoading,
                ) {
                    Text(
                        if (usesCategoryId) {
                            "Gunakan ID kategori"
                        } else {
                            "Tanpa kategori"
                        },
                    )
                }
    
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Tanpa kategori") },
                        onClick = {
                            usesCategoryId = false
                            categoryId = ""
                            fieldErrors = fieldErrors.copy(categoryId = null)
                            categoryMenuExpanded = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    DropdownMenuItem(
                        text = { Text("Gunakan ID kategori") },
                        onClick = {
                            usesCategoryId = true
                            categoryMenuExpanded = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
    
            if (usesCategoryId) {
                OutlinedTextField(
                    value = categoryId,
                    onValueChange = {
                        categoryId = it
                        fieldErrors = fieldErrors.copy(categoryId = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = fieldErrors.categoryId != null,
                    label = { Text("ID Kategori") },
                    supportingText = fieldErrors.categoryId?.let { message ->
                        { Text(message) }
                    },
                    singleLine = true,
                )
            }
    
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .toggleable(
                        value = trackStock,
                        enabled = !isLoading,
                        role = Role.Switch,
                        onValueChange = { trackStock = it },
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kelola Stok",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Aktifkan jika stok produk perlu dihitung.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = trackStock,
                    onCheckedChange = null,
                    enabled = !isLoading,
                )
            }
    
            if (state is ProductManagementState.Error) {
                Text(
                    text = (state as ProductManagementState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
    
            Button(
                onClick = {
                    when (
                        val result = validateCreateProductForm(
                            CreateProductFormInput(
                                name = name,
                                sku = sku,
                                price = price,
                                cost = cost,
                                minimumStock = minimumStock,
                                categoryId = if (usesCategoryId) categoryId else null,
                                trackStock = trackStock,
                            ),
                        )
                    ) {
                        is CreateProductFormResult.Invalid -> {
                            fieldErrors = result.errors
                        }
                        is CreateProductFormResult.Valid -> {
                            val product = result.product
                            viewModel.createProduct(
                                name = product.name,
                                sku = product.sku,
                                categoryId = product.categoryId,
                                price = product.price,
                                cost = product.cost,
                                minimumStock = product.minimumStock,
                                trackStock = product.trackStock,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Text("Menyimpan...")
                    }
                } else {
                    Text("Simpan Produk")
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = !isLoading,
            ) {
                Text("Batal")
            }
        }
    }
}
