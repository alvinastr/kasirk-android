package com.kasirkita.pos.presentation.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.theme.KasirSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val editingCategory by viewModel.editingCategory.collectAsState()
    val deletingCategory by viewModel.deletingCategory.collectAsState()
    val context = LocalContext.current

    CategoryManagementContent(
        state = state,
        onBack = onNavigateBack,
        onAddCategory = { viewModel.showAddCategoryDialog() },
        onEditCategory = { category -> viewModel.showEditCategoryDialog(category) },
        onDeleteCategory = { category -> viewModel.showDeleteConfirmation(category) },
        onRetry = { viewModel.loadCategories() },
    )

    if (showAddDialog) {
        CategoryDialog(
            title = "Tambah Kategori",
            initialName = "",
            onConfirm = { name -> viewModel.createCategory(name) },
            onDismiss = { viewModel.dismissAddDialog() },
        )
    }

    if (showEditDialog) {
        CategoryDialog(
            title = "Edit Kategori",
            initialName = editingCategory?.name ?: "",
            onConfirm = { name -> viewModel.updateCategory(name) },
            onDismiss = { viewModel.dismissEditDialog() },
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            categoryName = deletingCategory?.name ?: "",
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDeleteDialog() },
        )
    }
}

@Composable
internal fun CategoryManagementContent(
    state: CategoryManagementState,
    onBack: () -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KasirSpacing.Large),
        ) {
            CategoryManagementHeader(onBack = onBack, onAddCategory = onAddCategory)

            when (val currentState = state) {
                CategoryManagementState.Loading -> KasirLoadingState(
                    message = "Memuat kategori...",
                    modifier = Modifier.fillMaxSize(),
                )

                CategoryManagementState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        space = KasirSpacing.Large,
                        alignment = Alignment.CenterVertically,
                    ),
                ) {
                    KasirEmptyState(
                        message = "Belum ada kategori",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KasirPrimaryButton(
                        text = "Tambah Kategori",
                        onClick = onAddCategory,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is CategoryManagementState.Error -> KasirErrorState(
                    message = currentState.message,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )

                is CategoryManagementState.Success -> CategoryList(
                    categories = currentState.categories,
                    onEditCategory = onEditCategory,
                    onDeleteCategory = onDeleteCategory,
                )
            }
        }
    }
}

@Composable
private fun CategoryManagementHeader(
    onBack: () -> Unit,
    onAddCategory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
        ) {
            KasirTextButton(
                text = "Kembali",
                onClick = onBack,
            )
            Text(
                text = "Kelola Kategori",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        KasirPrimaryButton(
            text = "Tambah Kategori",
            onClick = onAddCategory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = KasirSpacing.XSmall),
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        items(
            items = categories,
            key = Category::id,
        ) { category ->
            CategoryRow(
                category = category,
                onEdit = { onEditCategory(category) },
                onDelete = { onDeleteCategory(category) },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit kategori",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Hapus kategori",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = KasirSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama Kategori") },
                    supportingText = error?.let { { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            KasirPrimaryButton(
                text = "Simpan",
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onConfirm(name.trim())
                    } else {
                        error = "Nama kategori tidak boleh kosong"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            KasirTextButton(
                text = "Batal",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Hapus Kategori", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "Yakin ingin menghapus kategori \"$categoryName\"? Kategori yang sudah dipakai produk tidak dapat dihapus.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            KasirPrimaryButton(
                text = "Hapus",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            KasirTextButton(
                text = "Batal",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}