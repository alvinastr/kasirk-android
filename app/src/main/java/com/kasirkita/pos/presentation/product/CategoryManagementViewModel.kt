package com.kasirkita.pos.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Category
import com.kasirkita.pos.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CategoryManagementState>(
        CategoryManagementState.Loading,
    )
    val state: StateFlow<CategoryManagementState> = _state.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _editingCategory = MutableStateFlow<Category?>(null)
    val editingCategory: StateFlow<Category?> = _editingCategory.asStateFlow()

    private val _deletingCategory = MutableStateFlow<Category?>(null)
    val deletingCategory: StateFlow<Category?> = _deletingCategory.asStateFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _state.value = CategoryManagementState.Loading
            categoryRepository.getCategories().fold(
                onSuccess = { categories ->
                    _state.value = if (categories.isEmpty()) {
                        CategoryManagementState.Empty
                    } else {
                        CategoryManagementState.Success(categories)
                    }
                },
                onFailure = { throwable ->
                    _state.value = CategoryManagementState.Error(categoryManagementErrorMessage(throwable))
                },
            )
        }
    }

    fun showAddCategoryDialog() {
        _showAddDialog.value = true
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            categoryRepository.createCategory(name).fold(
                onSuccess = { category ->
                    dismissAddDialog()
                    loadCategories()
                },
                onFailure = { throwable ->
                    // Error is handled in dialog
                },
            )
        }
    }

    fun showEditCategoryDialog(category: Category) {
        _editingCategory.value = category
        _showEditDialog.value = true
    }

    fun dismissEditDialog() {
        _showEditDialog.value = false
        _editingCategory.value = null
    }

    fun updateCategory(name: String) {
        _editingCategory.value?.let { category ->
            viewModelScope.launch {
                categoryRepository.updateCategory(category.id, name).fold(
                    onSuccess = { updated ->
                        dismissEditDialog()
                        loadCategories()
                    },
                    onFailure = { throwable ->
                        // Error is handled in dialog
                    },
                )
            }
        }
    }

    fun showDeleteConfirmation(category: Category) {
        _deletingCategory.value = category
        _showDeleteDialog.value = true
    }

    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
        _deletingCategory.value = null
    }

    fun confirmDelete() {
        _deletingCategory.value?.let { category ->
            viewModelScope.launch {
                categoryRepository.deleteCategory(category.id).fold(
                    onSuccess = { _ ->
                        dismissDeleteDialog()
                        loadCategories()
                    },
                    onFailure = { throwable ->
                        // Error is handled in dialog
                    },
                )
            }
        }
    }
}

sealed interface CategoryManagementState {
    data object Loading : CategoryManagementState
    data object Empty : CategoryManagementState
    data class Success(val categories: List<Category>) : CategoryManagementState
    data class Error(val message: String) : CategoryManagementState
}

internal fun categoryManagementErrorMessage(throwable: Throwable): String = when {
    throwable is IOException ->
        "Kategori tidak dapat dimuat. Periksa koneksi lalu coba lagi."
    throwable is HttpException && throwable.code() == 401 ->
        "Sesi login berakhir. Silakan login kembali."
    throwable is HttpException && throwable.code() == 403 ->
        "Anda tidak memiliki izin untuk mengelola kategori."
    throwable is HttpException && throwable.code() == 409 ->
        "Kategori tidak dapat dihapus karena sudah digunakan oleh produk."
    else -> "Kategori tidak dapat dimuat. Coba lagi."
}