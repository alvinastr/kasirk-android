package com.kasirkita.pos.presentation.product

import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.model.SelectionMode
import com.kasirkita.pos.domain.repository.CategoryRepository
import com.kasirkita.pos.domain.repository.ModifierRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.usecase.CreateProductUseCase
import com.kasirkita.pos.domain.usecase.GetCategoriesUseCase
import com.kasirkita.pos.domain.usecase.GetModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.GetProductModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.ReplaceProductModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.UpdateProductUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProductManagementViewModelModifierTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createLoadsActiveGlobalGroups() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadModifierGroupsForCreate()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.modifierState.value
        assertTrue(state.assignmentsKnown)
        assertEquals(1, state.rows.size)
        assertEquals("ICE", state.rows.first().name)
        assertFalse(state.rows.first().selected)
    }

    @Test
    fun editLoadsAuthoritativeAssignmentsAndPreservesConfiguration() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(
                listOf(
                    ProductModifierAssignment(
                        productId = "product-1",
                        groupId = "ice",
                        required = true,
                        selectionMode = SelectionMode.MULTIPLE,
                        displayOrder = 3,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.modifierState.value
        assertTrue(state.assignmentsKnown)
        val row = state.rows.first()
        assertTrue(row.selected)
        assertTrue(row.required)
        assertEquals(SelectionMode.MULTIPLE, row.selectionType)
        assertEquals(3, row.displayOrder)
        assertFalse(viewModel.hasModifierAssignmentChanges())
    }

    @Test
    fun unchangedExistingAssignmentsAreNotChanged() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(
                listOf(
                    ProductModifierAssignment(
                        productId = "",
                        groupId = "ice",
                        required = false,
                        selectionMode = SelectionMode.SINGLE,
                        displayOrder = 1,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.modifierAssignmentsChanged())
    }

    @Test
    fun productIdOnlyDifferenceIsNotAChange() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(
                listOf(
                    ProductModifierAssignment(
                        productId = "product-1",
                        groupId = "ice",
                        required = false,
                        selectionMode = SelectionMode.SINGLE,
                        displayOrder = 1,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.modifierState.value.assignments.single().productId)
        assertFalse(viewModel.modifierAssignmentsChanged())
    }

    @Test
    fun requiredChangeIsDetected() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(
                listOf(
                    ProductModifierAssignment(
                        productId = "product-1",
                        groupId = "ice",
                        required = false,
                        selectionMode = SelectionMode.SINGLE,
                        displayOrder = 1,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setModifierGroupRequired("ice", true)

        assertTrue(viewModel.modifierAssignmentsChanged())
    }

    @Test
    fun selectionTypeChangeIsDetected() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(
                listOf(
                    ProductModifierAssignment(
                        productId = "product-1",
                        groupId = "ice",
                        required = false,
                        selectionMode = SelectionMode.SINGLE,
                        displayOrder = 1,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setModifierGroupSelectionType("ice", SelectionMode.MULTIPLE)

        assertTrue(viewModel.modifierAssignmentsChanged())
    }

    @Test
    fun groupSelectionAndDeselectionAreDetected() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(emptyList()),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleModifierGroup("ice")
        assertTrue(viewModel.modifierAssignmentsChanged())

        viewModel.toggleModifierGroup("ice")
        assertFalse(viewModel.modifierAssignmentsChanged())
    }

    @Test
    fun deselectingExistingGroupIsDetected() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(
                listOf(
                    ProductModifierAssignment(
                        productId = "product-1",
                        groupId = "ice",
                        required = false,
                        selectionMode = SelectionMode.SINGLE,
                        displayOrder = 1,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleModifierGroup("ice")

        assertTrue(viewModel.modifierAssignmentsChanged())
    }

    @Test
    fun newlySelectedGroupDefaultsToOptionalSingle() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
        )
        val viewModel = viewModel(modRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForCreate()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        val row = viewModel.modifierState.value.rows.first()
        assertTrue(row.selected)
        assertFalse(row.required)
        assertEquals(SelectionMode.SINGLE, row.selectionType)
        assertEquals(1, row.displayOrder)
    }

    @Test
    fun modifierOnlyEditTriggersPutWithoutPatch() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(emptyList()),
        )
        val prodRepo = FakeProductRepository()
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProduct(
            productId = "product-1",
            modifierAssignmentsChanged = true,
            existingProduct = product("product-1"),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, prodRepo.updateProductCalls)
        assertEquals(1, modRepo.replaceModifierGroupsCalls)
        assertEquals(
            listOf(
                ProductModifierAssignment(
                    productId = "",
                    groupId = "ice",
                    required = false,
                    selectionMode = SelectionMode.SINGLE,
                    displayOrder = 1,
                ),
            ),
            modRepo.lastReplacedAssignments,
        )
    }

    @Test
    fun scalarOnlyEditDoesNotPut() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(emptyList()),
        )
        val prodRepo = FakeProductRepository()
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProduct(
            productId = "product-1",
            name = "New name",
            modifierAssignmentsChanged = false,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, prodRepo.updateProductCalls)
        assertEquals(0, modRepo.replaceModifierGroupsCalls)
    }

    @Test
    fun scalarAndModifierEditPatchesThenPuts() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(emptyList()),
        )
        val prodRepo = FakeProductRepository()
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProduct(
            productId = "product-1",
            name = "New name",
            modifierAssignmentsChanged = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, prodRepo.updateProductCalls)
        assertEquals(1, modRepo.replaceModifierGroupsCalls)
    }

    @Test
    fun patchFailurePreventsPut() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(emptyList()),
        )
        val prodRepo = FakeProductRepository(
            updateResult = Result.failure(httpException(400, "{}")),
        )
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProduct(
            productId = "product-1",
            name = "New name",
            modifierAssignmentsChanged = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, prodRepo.updateProductCalls)
        assertEquals(0, modRepo.replaceModifierGroupsCalls)
        assertTrue(viewModel.state.value is ProductManagementState.Error)
    }

    @Test
    fun assignmentLoadFailureNeverSendsPut() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.failure(IllegalStateException("network down")),
        )
        val prodRepo = FakeProductRepository()
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.modifierState.value.assignmentsKnown)
        viewModel.updateProduct(
            productId = "product-1",
            name = "New name",
            modifierAssignmentsChanged = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, prodRepo.updateProductCalls)
        assertEquals(0, modRepo.replaceModifierGroupsCalls)
    }

    @Test
    fun explicitDeselectAllAfterSuccessfulLoadSendsIntentionalEmptyPut() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(
                listOf(
                    ProductModifierAssignment(
                        productId = "product-1",
                        groupId = "ice",
                        required = false,
                        selectionMode = SelectionMode.SINGLE,
                        displayOrder = 1,
                    ),
                ),
            ),
        )
        val prodRepo = FakeProductRepository()
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProduct(
            productId = "product-1",
            modifierAssignmentsChanged = true,
            existingProduct = product("product-1"),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, modRepo.replaceModifierGroupsCalls)
        assertTrue(modRepo.lastReplacedAssignments.isEmpty())
    }

    @Test
    fun putFailureAfterPatchReportsPartialSuccess() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(emptyList()),
            replaceResult = Result.failure(IllegalStateException("modifier save failed")),
        )
        val prodRepo = FakeProductRepository()
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProduct(
            productId = "product-1",
            name = "New name",
            modifierAssignmentsChanged = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ProductManagementState.Error)
        assertTrue((state as ProductManagementState.Error).message.contains("Data produk tersimpan"))
    }

    @Test
    fun createSuccessThenAssignmentPut() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
        )
        val prodRepo = FakeProductRepository(
            createResult = Result.success(product("product-new")),
        )
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForCreate()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createProduct(
            name = "Es",
            sku = "ES-1",
            categoryId = null,
            price = 1000L,
            cost = 500L,
            minimumStock = 1,
            trackStock = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, prodRepo.createProductCalls)
        assertEquals(1, modRepo.replaceModifierGroupsCalls)
        assertEquals("product-new", modRepo.lastReplacedProductId)
    }

    @Test
    fun createFailurePreventsAssignmentPut() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
        )
        val prodRepo = FakeProductRepository(
            createResult = Result.failure(httpException(400, "{}")),
        )
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForCreate()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createProduct(
            name = "Es",
            sku = "ES-1",
            categoryId = null,
            price = 1000L,
            cost = 500L,
            minimumStock = 1,
            trackStock = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, prodRepo.createProductCalls)
        assertEquals(0, modRepo.replaceModifierGroupsCalls)
    }

    @Test
    fun createSuccessWithAssignmentFailurePreservesCreatedProduct() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            replaceResult = Result.failure(IllegalStateException("modifier save failed")),
        )
        val prodRepo = FakeProductRepository(
            createResult = Result.success(product("product-new")),
        )
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForCreate()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createProduct(
            name = "Es",
            sku = "ES-1",
            categoryId = null,
            price = 1000L,
            cost = 500L,
            minimumStock = 1,
            trackStock = true,
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ProductManagementState.Error)
        assertTrue((state as ProductManagementState.Error).message.contains("Produk tersimpan"))
        assertEquals(1, prodRepo.createProductCalls)
    }

    @Test
    fun forbiddenAssignmentFailureMapsToPermissionMessage() {
        val modRepo = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(listOf(globalGroup("ice", "ICE"))))),
            productAssignments = Result.success(emptyList()),
            replaceResult = Result.failure(httpException(403, "{}")),
        )
        val prodRepo = FakeProductRepository()
        val viewModel = viewModel(modRepo, prodRepo)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroupsForEdit("product-1")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleModifierGroup("ice")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProduct(
            productId = "product-1",
            modifierAssignmentsChanged = true,
            existingProduct = product("product-1"),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ProductManagementState.Error)
        assertTrue((state as ProductManagementState.Error).message.contains("OWNER/ADMIN"))
    }

    private fun viewModel(
        modifierRepository: ModifierRepository,
        productRepository: ProductRepository = FakeProductRepository(),
    ) = ProductManagementViewModel(
        createProductUseCase = CreateProductUseCase(productRepository),
        updateProductUseCase = UpdateProductUseCase(productRepository),
        getCategoriesUseCase = GetCategoriesUseCase(FakeCategoryRepository()),
        getModifierGroupsUseCase = GetModifierGroupsUseCase(modifierRepository),
        getProductModifierGroupsUseCase = GetProductModifierGroupsUseCase(modifierRepository),
        replaceProductModifierGroupsUseCase = ReplaceProductModifierGroupsUseCase(modifierRepository),
        productRepository = productRepository,
    )

    private fun globalGroup(id: String, name: String) = ModifierGroup(
        id = id,
        tenantId = "tenant-id",
        name = name,
        isActive = true,
        displayOrder = 1,
        required = false,
        selectionType = SelectionMode.SINGLE,
        options = emptyList(),
    )

    private fun product(id: String) = Product(
        id = id,
        tenantId = "tenant-id",
        categoryId = null,
        name = "Produk",
        sku = "SKU-1",
        price = 1000L,
        cost = 500L,
        minimumStock = 0,
        trackStock = true,
        isActive = true,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            body.toResponseBody("application/json".toMediaType()),
        ),
    )

    private class FakeModifierRepository(
        private val getResults: ArrayDeque<Result<List<ModifierGroup>>>,
        private val productAssignments: Result<List<ProductModifierAssignment>> = Result.success(emptyList()),
        private val replaceResult: Result<List<ProductModifierAssignment>> = Result.success(emptyList()),
    ) : ModifierRepository {
        var getModifierGroupsCalls = 0
        var replaceModifierGroupsCalls = 0
        var lastReplacedProductId: String? = null
        var lastReplacedAssignments: List<ProductModifierAssignment> = emptyList()

        override suspend fun getModifierGroups(includeInactive: Boolean): Result<List<ModifierGroup>> {
            getModifierGroupsCalls += 1
            return getResults.removeFirst()
        }

        override suspend fun getProductModifierGroups(productId: String): Result<List<ProductModifierAssignment>> =
            productAssignments

        override suspend fun replaceModifierGroups(
            productId: String,
            assignments: List<ProductModifierAssignment>,
        ): Result<List<ProductModifierAssignment>> {
            replaceModifierGroupsCalls += 1
            lastReplacedProductId = productId
            lastReplacedAssignments = assignments
            return replaceResult
        }

        override suspend fun getModifierGroup(groupId: String): Result<ModifierGroup> = error("Not used")
        override suspend fun createModifierGroup(
            name: String,
            isActive: Boolean,
            displayOrder: Int,
        ): Result<ModifierGroup> = error("Not used")
        override suspend fun updateModifierGroup(
            groupId: String,
            name: String?,
            isActive: Boolean?,
            displayOrder: Int?,
        ): Result<ModifierGroup> = error("Not used")
        override suspend fun deleteModifierGroup(groupId: String): Result<Unit> = error("Not used")
        override suspend fun createModifierOption(
            groupId: String,
            name: String,
            priceDelta: Long,
            isActive: Boolean,
            displayOrder: Int,
        ): Result<com.kasirkita.pos.domain.model.ModifierOption> = error("Not used")
        override suspend fun updateModifierOption(
            groupId: String,
            optionId: String,
            name: String?,
            priceDelta: Long?,
            isActive: Boolean?,
            displayOrder: Int?,
        ): Result<com.kasirkita.pos.domain.model.ModifierOption> = error("Not used")
        override suspend fun deleteModifierOption(groupId: String, optionId: String): Result<Unit> = error("Not used")
        override suspend fun assignModifierGroup(
            productId: String,
            modifierGroupId: String,
            required: Boolean,
            selectionType: String,
            displayOrder: Int,
        ): Result<ProductModifierAssignment> = error("Not used")
        override suspend fun updateModifierGroupAssignment(
            productId: String,
            groupId: String,
            modifierGroupId: String,
            required: Boolean,
            selectionType: String,
            displayOrder: Int,
        ): Result<ProductModifierAssignment> = error("Not used")
        override suspend fun removeModifierGroup(productId: String, groupId: String): Result<Unit> = error("Not used")
    }

    private class FakeProductRepository(
        private val createResult: Result<Product>? = null,
        private val updateResult: Result<Product>? = null,
    ) : ProductRepository {
        var updateProductCalls = 0
        var createProductCalls = 0

        override suspend fun getProducts(
            query: String?,
            categoryId: String?,
            includeModifiers: Boolean?,
        ): Result<List<Product>> = Result.success(emptyList())
        override suspend fun refreshProducts(
            query: String?,
            categoryId: String?,
            includeModifiers: Boolean?,
        ): Result<List<Product>> = Result.success(emptyList())
        override suspend fun createProduct(
            name: String,
            sku: String,
            categoryId: String?,
            price: Long,
            cost: Long,
            minimumStock: Int,
            trackStock: Boolean,
        ): Result<Product> {
            createProductCalls += 1
            return createResult ?: Result.success(
                Product(
                    id = "product-new",
                    tenantId = "tenant-id",
                    categoryId = categoryId,
                    name = name,
                    sku = sku,
                    price = price,
                    cost = cost,
                    minimumStock = minimumStock,
                    trackStock = trackStock,
                    isActive = true,
                    createdAt = "2026-01-01T00:00:00Z",
                ),
            )
        }

        override suspend fun updateProduct(
            productId: String,
            name: String?,
            sku: String?,
            categoryId: String?,
            categoryIdChanged: Boolean,
            price: Long?,
            cost: Long?,
            minimumStock: Int?,
            trackStock: Boolean?,
        ): Result<Product> {
            updateProductCalls += 1
            return updateResult ?: Result.success(
                Product(
                    id = productId,
                    tenantId = "tenant-id",
                    categoryId = categoryId,
                    name = name ?: "Produk",
                    sku = sku ?: "SKU-1",
                    price = price ?: 1000L,
                    cost = cost ?: 500L,
                    minimumStock = minimumStock ?: 0,
                    trackStock = trackStock ?: true,
                    isActive = true,
                    createdAt = "2026-01-01T00:00:00Z",
                ),
            )
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        override suspend fun getCategories(): Result<List<com.kasirkita.pos.domain.model.Category>> =
            Result.success(emptyList())
        override suspend fun createCategory(name: String): Result<com.kasirkita.pos.domain.model.Category> = error("Not used")
        override suspend fun updateCategory(id: String, name: String): Result<com.kasirkita.pos.domain.model.Category> = error("Not used")
        override suspend fun deleteCategory(id: String): Result<Unit> = error("Not used")
    }
}
