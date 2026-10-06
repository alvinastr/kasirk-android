package com.kasirkita.pos.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.model.SelectionMode
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.usecase.CreateProductUseCase
import com.kasirkita.pos.domain.usecase.GetCategoriesUseCase
import com.kasirkita.pos.domain.usecase.GetModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.GetProductModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.ReplaceProductModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.UpdateProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/**
 * Modifier-assignment sub-state for the shared product form.
 *
 * [assignmentsLoaded] is deliberately separate from [groups] so a failed load can never be
 * mistaken for "this product has no modifiers". [assignmentsKnown] is the only guard that may
 * authorise sending a replacement set to the backend.
 */
data class ProductModifierSectionState(
    val loading: Boolean = false,
    val loadError: String? = null,
    val assignmentsLoaded: Boolean = false,
    val originalAssignments: List<ProductModifierAssignment> = emptyList(),
    val rows: List<ProductModifierGroupRow> = emptyList(),
) {
    val assignmentsKnown: Boolean
        get() = assignmentsLoaded && loadError == null

    val assignments: List<ProductModifierAssignment>
        get() = rows
            .filter { it.selected }
            .map { row ->
                row.assignment.copy(
                    productId = "",
                    displayOrder = row.displayOrder,
                )
            }
}

private data class ModifierAssignmentSignature(
    val groupId: String,
    val required: Boolean,
    val selectionMode: SelectionMode,
    val displayOrder: Int,
)

data class ProductModifierGroupRow(
    val groupId: String,
    val name: String,
    val selected: Boolean,
    val required: Boolean,
    val selectionType: SelectionMode,
    val displayOrder: Int,
    val globalDisplayOrder: Int,
) {
    val assignment: ProductModifierAssignment
        get() = ProductModifierAssignment(
            productId = "",
            groupId = groupId,
            required = required,
            selectionMode = selectionType,
            displayOrder = displayOrder,
        )
}

@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getModifierGroupsUseCase: GetModifierGroupsUseCase,
    private val getProductModifierGroupsUseCase: GetProductModifierGroupsUseCase,
    private val replaceProductModifierGroupsUseCase: ReplaceProductModifierGroupsUseCase,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProductManagementState>(
        ProductManagementState.Idle,
    )
    val state: StateFlow<ProductManagementState> = _state.asStateFlow()

    private val _categoryState = MutableStateFlow<CategoryState>(CategoryState.Loading)
    val categoryState: StateFlow<CategoryState> = _categoryState.asStateFlow()

    private val _modifierState = MutableStateFlow(ProductModifierSectionState())
    val modifierState: StateFlow<ProductModifierSectionState> = _modifierState.asStateFlow()

    private var categoryLoadJob: Job? = null
    private var modifierLoadJob: Job? = null

    /**
     * Product created successfully while its modifier assignment still failed. Retrying the
     * modifier assignment must reuse this id instead of creating a second product.
     */
    private var pendingAssignmentRetryProduct: Product? = null

    init {
        loadCategories()
    }

    fun clearError() {
        if (_state.value is ProductManagementState.Error) {
            _state.value = ProductManagementState.Idle
        }
    }

    fun loadCategories() {
        if (categoryLoadJob?.isActive == true) return

        categoryLoadJob = viewModelScope.launch {
            _categoryState.value = CategoryState.Loading
            getCategoriesUseCase().fold(
                onSuccess = { categories ->
                    _categoryState.value = if (categories.isEmpty()) {
                        CategoryState.Empty
                    } else {
                        CategoryState.Success(categories)
                    }
                },
                onFailure = { throwable ->
                    _categoryState.value = CategoryState.Error(
                        categoryErrorMessage(throwable),
                    )
                },
            )
        }
    }

    /**
     * Loads the active global modifier groups backing the picker. Create Product has no existing
     * assignments, so this is the only modifier load it needs.
     */
    fun loadModifierGroupsForCreate() {
        loadModifierGroups(productId = null)
    }

    /**
     * Edit Product loads the assignable global groups and then the authoritative assignments of
     * the product being edited so existing `required` / `selectionType` survive the round trip.
     */
    fun loadModifierGroupsForEdit(productId: String) {
        loadModifierGroups(productId = productId)
    }

    private fun loadModifierGroups(productId: String?) {
        if (modifierLoadJob?.isActive == true) return

        // A retry after a failed load must not silently turn "unknown" into "empty".
        _modifierState.value = ProductModifierSectionState()
        modifierLoadJob = viewModelScope.launch {
            _modifierState.update {
                it.copy(
                    loading = true,
                    loadError = null,
                    assignmentsLoaded = false,
                )
            }

            val groupsResult = getModifierGroupsUseCase(includeInactive = false)
            groupsResult.fold(
                onSuccess = { groups ->
                    _modifierState.update { current ->
                        current.copy(
                            rows = current.seedRows(groups),
                        )
                    }

                    if (productId == null) {
                        _modifierState.update {
                            it.copy(
                                loading = false,
                                loadError = null,
                                assignmentsLoaded = true,
                                originalAssignments = emptyList(),
                            )
                        }
                    } else {
                        loadAssignmentsFor(productId)
                    }
                },
                onFailure = { throwable ->
                    _modifierState.update {
                        it.copy(
                            loading = false,
                            loadError = modifierLoadErrorMessage(throwable),
                            assignmentsLoaded = false,
                            rows = emptyList(),
                        )
                    }
                },
            )
        }
    }

    private suspend fun loadAssignmentsFor(productId: String) {
        _modifierState.update {
            it.copy(
                loading = true,
                loadError = null,
                assignmentsLoaded = false,
            )
        }

        getProductModifierGroupsUseCase(productId).fold(
            onSuccess = { assignments ->
                _modifierState.update { current ->
                    current.copy(
                        loading = false,
                        loadError = null,
                        assignmentsLoaded = true,
                        originalAssignments = assignments,
                        rows = current.rows.map { row ->
                            val assignment = assignments.firstOrNull { it.groupId == row.groupId }
                            if (assignment == null) {
                                row.copy(
                                    selected = false,
                                    required = false,
                                    selectionType = SelectionMode.SINGLE,
                                    displayOrder = row.globalDisplayOrder,
                                )
                            } else {
                                row.copy(
                                    selected = true,
                                    required = assignment.required,
                                    selectionType = assignment.selectionMode,
                                    displayOrder = assignment.displayOrder,
                                )
                            }
                        }.sortedWith(
                            compareBy({ !it.selected }, { it.displayOrder }),
                        ),
                    )
                }
            },
            onFailure = { throwable ->
                // Unknown, not empty: keep rows out of the picture and block assignment writes.
                _modifierState.update {
                    it.copy(
                        loading = false,
                        loadError = modifierLoadErrorMessage(throwable),
                        assignmentsLoaded = false,
                    )
                }
            },
        )
    }

    fun retryModifierLoad(productId: String?) {
        modifierLoadJob = null
        loadModifierGroups(productId)
    }

    fun toggleModifierGroup(groupId: String) {
        _modifierState.update { current ->
            if (!current.assignmentsKnown) return@update current
            current.copy(
                rows = current.rows.map { row ->
                    if (row.groupId != groupId) {
                        row
                    } else if (row.selected) {
                        row.copy(selected = false)
                    } else {
                        // Newly selected groups start from the safe defaults.
                        row.copy(
                            selected = true,
                            required = false,
                            selectionType = SelectionMode.SINGLE,
                        )
                    }
                },
            ).withDisplayOrderResequenced()
        }
    }

    fun setModifierGroupRequired(groupId: String, required: Boolean) {
        _modifierState.update { current ->
            if (!current.assignmentsKnown) return@update current
            current.copy(
                rows = current.rows.map { row ->
                    if (row.groupId == groupId) row.copy(required = required) else row
                },
            )
        }
    }

    fun setModifierGroupSelectionType(groupId: String, selectionType: SelectionMode) {
        _modifierState.update { current ->
            if (!current.assignmentsKnown) return@update current
            current.copy(
                rows = current.rows.map { row ->
                    if (row.groupId == groupId) row.copy(selectionType = selectionType) else row
                },
            )
        }
    }

    /**
     * True only when the outgoing assignment set differs from what the server holds. Returns false
     * while the state is unknown so a failed load can never trigger a PUT.
     */
    fun hasModifierAssignmentChanges(): Boolean {
        val current = _modifierState.value
        if (!current.assignmentsKnown) return false

        return assignmentSignatures(current.assignments) != assignmentSignatures(current.originalAssignments)
    }

    private fun assignmentSignatures(
        assignments: List<ProductModifierAssignment>,
    ): List<ModifierAssignmentSignature> = assignments
        .map { assignment ->
            ModifierAssignmentSignature(
                groupId = assignment.groupId,
                required = assignment.required,
                selectionMode = assignment.selectionMode,
                displayOrder = assignment.displayOrder,
            )
        }
        .sortedWith(compareBy({ it.displayOrder }, { it.groupId }))

    /** Semantic assignment fields used by both UI change detection and save-flow gating. */
    fun modifierAssignmentsChanged(): Boolean = hasModifierAssignmentChanges()

    fun createProduct(
        name: String,
        sku: String,
        categoryId: String?,
        price: Long,
        cost: Long,
        minimumStock: Int,
        trackStock: Boolean,
    ) {
        if (_state.value is ProductManagementState.Loading) return

        viewModelScope.launch {
            _state.value = ProductManagementState.Loading

            val retryProduct = pendingAssignmentRetryProduct
            if (retryProduct != null) {
                // The product already exists; only the assignment failed. Never create a duplicate.
                pendingAssignmentRetryProduct = null
                saveAssignmentFor(retryProduct, productJustSaved = true)
                return@launch
            }

            createProductUseCase(
                name = name,
                sku = sku,
                categoryId = categoryId,
                price = price,
                cost = cost,
                minimumStock = minimumStock,
                trackStock = trackStock,
            ).fold(
                onSuccess = { product ->
                    saveAssignmentFor(product, productJustSaved = true)
                },
                onFailure = { throwable ->
                    // Product creation failed: the assignment PUT must never run.
                    _state.value = ProductManagementState.Error(
                        productManagementErrorMessage(throwable),
                    )
                },
            )
        }
    }

    fun saveModifierAssignments(product: Product) {
        if (_state.value is ProductManagementState.Loading) return
        viewModelScope.launch {
            _state.value = ProductManagementState.Loading
            saveAssignmentFor(
                product = product,
                productJustSaved = false,
                modifierAssignmentsChanged = true,
            )
        }
    }

    fun updateProduct(
        productId: String,
        name: String? = null,
        sku: String? = null,
        categoryId: String? = null,
        categoryIdChanged: Boolean = false,
        price: Long? = null,
        cost: Long? = null,
        minimumStock: Int? = null,
        trackStock: Boolean? = null,
        modifierAssignmentsChanged: Boolean = false,
        existingProduct: Product? = null,
    ) {
        if (_state.value is ProductManagementState.Loading) return

        viewModelScope.launch {
            _state.value = ProductManagementState.Loading

            val scalarChanges = name != null ||
                sku != null ||
                categoryIdChanged ||
                price != null ||
                cost != null ||
                minimumStock != null ||
                trackStock != null
            if (!scalarChanges) {
                saveAssignmentFor(
                    product = requireNotNull(existingProduct) {
                        "Existing product is required for modifier-only save"
                    },
                    productJustSaved = false,
                    modifierAssignmentsChanged = modifierAssignmentsChanged,
                )
                return@launch
            }

            updateProductUseCase(
                productId = productId,
                name = name,
                sku = sku,
                categoryId = categoryId,
                categoryIdChanged = categoryIdChanged,
                price = price,
                cost = cost,
                minimumStock = minimumStock,
                trackStock = trackStock,
            ).fold(
                onSuccess = { product ->
                    // PATCH succeeded. Only now may the assignment PUT run.
                    saveAssignmentFor(
                        product = product,
                        productJustSaved = false,
                        modifierAssignmentsChanged = modifierAssignmentsChanged,
                    )
                },
                onFailure = { throwable ->
                    // PATCH failed: no PUT. Report plain product failure.
                    _state.value = ProductManagementState.Error(
                        productManagementErrorMessage(throwable),
                    )
                },
            )
        }
    }

    private suspend fun saveAssignmentFor(
        product: Product,
        productJustSaved: Boolean,
        modifierAssignmentsChanged: Boolean = false,
    ) {
        val modifierState = _modifierState.value
        if (productJustSaved && !modifierState.assignmentsKnown) {
            // Unknown on create: create must remain valid; never send PUT because load failed.
            _state.value = ProductManagementState.Success(product)
            return
        }

        if (!productJustSaved && !modifierAssignmentsChanged) {
            // Scalar-only edit: nothing to replace.
            _state.value = ProductManagementState.Success(product)
            return
        }

        if (!modifierState.assignmentsKnown) {
            // Edit path: never send PUT [] because loading failed or has not completed.
            _state.value = ProductManagementState.Error(
                "Data modifier belum dapat dimuat. Muat ulang modifier sebelum menyimpan.",
            )
            return
        }

        if (productJustSaved && modifierState.assignments.isEmpty()) {
            // Creating a product with no selected modifiers is valid; no PUT needed.
            _state.value = ProductManagementState.Success(product)
            return
        }

        replaceProductModifierGroupsUseCase(
            productId = product.id,
            assignments = modifierState.assignments,
        ).fold(
            onSuccess = {
                // Refresh canonical cache so POS runtime sees Product.modifierGroups without restart.
                productRepository.refreshProducts(includeModifiers = true)
                _state.value = ProductManagementState.Success(product)
            },
            onFailure = { throwable ->
                pendingAssignmentRetryProduct = if (productJustSaved) product else null
                _state.value = ProductManagementState.Error(
                    modifierSaveErrorMessage(
                        throwable = throwable,
                        productJustSaved = productJustSaved,
                    ),
                )
            },
        )
    }
}

/**
 * Seeds picker rows from the global groups, ordered by the global display order so newly selected
 * groups get a deterministic, contiguous display order.
 */
private fun ProductModifierSectionState.seedRows(
    groups: List<ModifierGroup>,
): List<ProductModifierGroupRow> = groups
    .filter { it.isActive }
    .sortedWith(compareBy({ it.displayOrder }, { it.name }))
    .mapIndexed { index, group ->
        ProductModifierGroupRow(
            groupId = group.id,
            name = group.name,
            selected = false,
            required = false,
            selectionType = SelectionMode.SINGLE,
            displayOrder = index + 1,
            globalDisplayOrder = index + 1,
        )
    }

/**
 * Display order is derived, never edited by hand: selected rows are renumbered 1..n in their current
 * picker order, unselected rows keep their global position.
 */
private fun ProductModifierSectionState.withDisplayOrderResequenced(): ProductModifierSectionState {
    var nextOrder = 1
    return copy(
        rows = rows.map { row ->
            if (row.selected) {
                row.copy(displayOrder = nextOrder++)
            } else {
                row
            }
        },
    )
}

internal fun modifierLoadErrorMessage(throwable: Throwable): String = when {
    throwable is IOException ->
        "Modifier tidak dapat dimuat. Periksa koneksi lalu coba lagi."
    throwable is HttpException && throwable.code() == 401 ->
        "Sesi login berakhir. Silakan login kembali."
    throwable is HttpException && throwable.code() == 403 ->
        "Anda tidak memiliki izin untuk melihat modifier."
    else -> "Modifier tidak dapat dimuat. Coba lagi."
}

internal fun modifierSaveErrorMessage(
    throwable: Throwable,
    productJustSaved: Boolean,
): String {
    val permission = throwable is HttpException &&
        (throwable.code() == 403 || "FORBIDDEN" in throwable.responseBodyText().uppercase(Locale.ROOT))
    if (permission) {
        return "Anda tidak memiliki izin untuk menyimpan modifier produk. Aksi ini hanya untuk OWNER/ADMIN."
    }

    val prefix = if (productJustSaved) {
        "Produk tersimpan, tetapi modifier gagal disimpan"
    } else {
        "Data produk tersimpan, tetapi modifier gagal disimpan"
    }
    val detail = throwable.message?.takeIf { it.isNotBlank() } ?: "Coba lagi."
    return "$prefix: $detail"
}

private fun HttpException.responseBodyText(): String = runCatching {
    response()?.errorBody()?.string()
}.getOrNull().orEmpty()

internal fun categoryErrorMessage(throwable: Throwable): String = when {
    throwable is IOException ->
        "Kategori tidak dapat dimuat. Periksa koneksi lalu coba lagi."
    throwable is HttpException && throwable.code() == 401 ->
        "Sesi login berakhir. Silakan login kembali."
    throwable is HttpException && throwable.code() == 403 ->
        "Anda tidak memiliki izin untuk melihat kategori."
    else -> "Kategori tidak dapat dimuat. Coba lagi."
}

internal fun productManagementErrorMessage(throwable: Throwable): String {
    if (throwable is HttpException) {
        val errorBody = runCatching {
            throwable.response()?.errorBody()?.string()
        }.getOrNull().orEmpty()
        val normalizedBody = errorBody.uppercase(Locale.ROOT)

        return when {
            "SKU_ALREADY_EXISTS" in normalizedBody ->
                "SKU sudah digunakan. Gunakan SKU lain."
            "CATEGORY_NOT_FOUND" in normalizedBody ->
                "Kategori tidak ditemukan. Pilih kategori lain atau gunakan Tanpa kategori."
            "PRODUCT_NOT_FOUND" in normalizedBody ->
                "Produk tidak ditemukan. Daftar produk mungkin sudah berubah."
            "VALIDATION_ERROR" in normalizedBody ||
                throwable.code() == 400 ||
                throwable.code() == 422 ->
                "Data produk belum valid. Periksa kembali data yang wajib diisi."
            throwable.code() == 401 ->
                "Sesi login berakhir. Silakan login kembali."
            throwable.code() == 403 ->
                "Anda tidak memiliki izin untuk mengelola produk."
            else -> "Produk tidak dapat disimpan. Coba lagi."
        }
    }

    return if (throwable is IOException) {
        "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    } else {
        "Produk tidak dapat disimpan. Coba lagi."
    }
}
