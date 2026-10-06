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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.SelectionMode

internal data class CategoryOption(
    val id: String?,
    val label: String,
)

internal fun categoryOptions(categories: List<Category>): List<CategoryOption> =
    listOf(CategoryOption(id = null, label = "Tanpa kategori")) +
        categories
            .sortedBy { category -> category.name }
            .map { category ->
                CategoryOption(id = category.id, label = category.name)
            }

internal fun categorySelectionLabel(
    selectedCategoryId: String?,
    options: List<CategoryOption>,
): String = options.firstOrNull { option -> option.id == selectedCategoryId }?.label
    ?: "Kategori saat ini"

internal data class ProductFormInitialValues(
    val name: String = "",
    val sku: String = "",
    val price: String = "",
    val cost: String = "",
    val minimumStock: String = "",
    val categoryId: String? = null,
    val trackStock: Boolean = true,
)

internal fun Product.toProductFormInitialValues(): ProductFormInitialValues =
    ProductFormInitialValues(
        name = name,
        sku = sku,
        price = price.toString(),
        cost = cost.toString(),
        minimumStock = minimumStock.toString(),
        categoryId = categoryId,
        trackStock = trackStock,
    )

@Composable
internal fun ProductForm(
    title: String,
    description: String,
    submitLabel: String,
    initialValues: ProductFormInitialValues,
    categoryState: CategoryState,
    modifierState: ProductModifierSectionState,
    isLoading: Boolean,
    errorMessage: String?,
    onInputChanged: () -> Unit,
    onRetryCategories: () -> Unit,
    onRetryModifiers: () -> Unit,
    onToggleModifierGroup: (String) -> Unit,
    onModifierRequiredChanged: (String, Boolean) -> Unit,
    onModifierSelectionTypeChanged: (String, SelectionMode) -> Unit,
    onSubmit: (ValidatedCreateProduct) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(initialValues.name) {
        mutableStateOf(initialValues.name)
    }
    var sku by rememberSaveable(initialValues.sku) {
        mutableStateOf(initialValues.sku)
    }
    var price by rememberSaveable(initialValues.price) {
        mutableStateOf(initialValues.price)
    }
    var cost by rememberSaveable(initialValues.cost) {
        mutableStateOf(initialValues.cost)
    }
    var minimumStock by rememberSaveable(initialValues.minimumStock) {
        mutableStateOf(initialValues.minimumStock)
    }
    var categoryId by rememberSaveable(initialValues.categoryId) {
        mutableStateOf(initialValues.categoryId)
    }
    var trackStock by rememberSaveable(initialValues.trackStock) {
        mutableStateOf(initialValues.trackStock)
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var fieldErrors by remember(initialValues) {
        mutableStateOf(CreateProductFormErrors())
    }
    val availableCategories = (categoryState as? CategoryState.Success)
        ?.categories
        .orEmpty()
    val categoryOptions = remember(availableCategories) {
        categoryOptions(availableCategories)
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    fieldErrors = fieldErrors.copy(name = null)
                    onInputChanged()
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
                    onInputChanged()
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
                    onInputChanged()
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
                    onInputChanged()
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
                    onInputChanged()
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
                        categorySelectionLabel(categoryId, categoryOptions),
                    )
                }

                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    categoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                categoryId = option.id
                                fieldErrors = fieldErrors.copy(categoryId = null)
                                categoryMenuExpanded = false
                                onInputChanged()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }

            when (categoryState) {
                CategoryState.Loading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Memuat kategori...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                CategoryState.Empty -> Text(
                    text = "Belum ada kategori. Produk tetap dapat disimpan tanpa kategori.",
                    style = MaterialTheme.typography.bodySmall,
                )
                is CategoryState.Error -> {
                    Text(
                        text = categoryState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = onRetryCategories,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        enabled = !isLoading,
                    ) {
                        Text("Coba Muat Kategori")
                    }
                }
                is CategoryState.Success -> Unit
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .toggleable(
                        value = trackStock,
                        enabled = !isLoading,
                        role = Role.Switch,
                        onValueChange = {
                            trackStock = it
                            onInputChanged()
                        },
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

            ModifierAssignmentSection(
                state = modifierState,
                enabled = !isLoading,
                onRetry = onRetryModifiers,
                onToggleGroup = {
                    onToggleModifierGroup(it)
                    onInputChanged()
                },
                onRequiredChanged = { groupId, required ->
                    onModifierRequiredChanged(groupId, required)
                    onInputChanged()
                },
                onSelectionTypeChanged = { groupId, selectionType ->
                    onModifierSelectionTypeChanged(groupId, selectionType)
                    onInputChanged()
                },
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
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
                                categoryId = categoryId,
                                trackStock = trackStock,
                            ),
                        )
                    ) {
                        is CreateProductFormResult.Invalid -> {
                            fieldErrors = result.errors
                        }
                        is CreateProductFormResult.Valid -> onSubmit(result.product)
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
                    Text(submitLabel)
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

@Composable
private fun ModifierAssignmentSection(
    state: ProductModifierSectionState,
    enabled: Boolean,
    onRetry: () -> Unit,
    onToggleGroup: (String) -> Unit,
    onRequiredChanged: (String, Boolean) -> Unit,
    onSelectionTypeChanged: (String, SelectionMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Modifier / Add-on",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Pilih modifier global yang berlaku untuk produk ini.",
            style = MaterialTheme.typography.bodySmall,
        )

        when {
            state.loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Memuat modifier...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.loadError != null -> {
                Text(
                    text = state.loadError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    enabled = enabled,
                ) {
                    Text("Coba Muat Modifier")
                }
            }
            state.rows.isEmpty() -> Text(
                text = "Belum ada modifier aktif. Produk tetap dapat disimpan tanpa modifier.",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> state.rows.forEach { row ->
                ModifierAssignmentRow(
                    row = row,
                    enabled = enabled && state.assignmentsKnown,
                    onToggleGroup = onToggleGroup,
                    onRequiredChanged = onRequiredChanged,
                    onSelectionTypeChanged = onSelectionTypeChanged,
                )
            }
        }
    }
}

@Composable
private fun ModifierAssignmentRow(
    row: ProductModifierGroupRow,
    enabled: Boolean,
    onToggleGroup: (String) -> Unit,
    onRequiredChanged: (String, Boolean) -> Unit,
    onSelectionTypeChanged: (String, SelectionMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = row.selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onToggleGroup(row.groupId) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = row.selected,
                onCheckedChange = null,
                enabled = enabled,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (row.selected) "Terpasang" else "Tidak dipakai",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (row.selected) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RequiredOption(
                    label = "Opsional",
                    selected = !row.required,
                    enabled = enabled,
                    onClick = { onRequiredChanged(row.groupId, false) },
                )
                RequiredOption(
                    label = "Wajib",
                    selected = row.required,
                    enabled = enabled,
                    onClick = { onRequiredChanged(row.groupId, true) },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionTypeOption(
                    label = "Satu pilihan",
                    selected = row.selectionType == SelectionMode.SINGLE,
                    enabled = enabled,
                    onClick = { onSelectionTypeChanged(row.groupId, SelectionMode.SINGLE) },
                )
                SelectionTypeOption(
                    label = "Banyak pilihan",
                    selected = row.selectionType == SelectionMode.MULTIPLE,
                    enabled = enabled,
                    onClick = { onSelectionTypeChanged(row.groupId, SelectionMode.MULTIPLE) },
                )
            }
        }
    }
}

@Composable
private fun RequiredOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(label)
    }
}

@Composable
private fun SelectionTypeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(label)
    }
}
