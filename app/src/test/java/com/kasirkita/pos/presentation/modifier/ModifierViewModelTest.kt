package com.kasirkita.pos.presentation.modifier

import com.kasirkita.pos.domain.model.ModifierGroup
import com.kasirkita.pos.domain.model.ModifierOption
import com.kasirkita.pos.domain.model.ProductModifierAssignment
import com.kasirkita.pos.domain.model.SelectionMode
import com.kasirkita.pos.domain.repository.ModifierRepository
import com.kasirkita.pos.domain.usecase.CreateModifierGroupUseCase
import com.kasirkita.pos.domain.usecase.CreateModifierOptionUseCase
import com.kasirkita.pos.domain.usecase.DeleteModifierGroupUseCase
import com.kasirkita.pos.domain.usecase.DeleteModifierOptionUseCase
import com.kasirkita.pos.domain.usecase.GetModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.UpdateModifierGroupUseCase
import com.kasirkita.pos.domain.usecase.UpdateModifierOptionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ModifierViewModelTest {
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
    fun createSuccess_triggersRefreshAndNewGroupBecomesVisible() {
        val extra = modifierGroup(id = "group-extra", name = "Extra")
        val repository = FakeModifierRepository(
            getResults = ArrayDeque(
                listOf(
                    Result.success(emptyList()),
                    Result.success(listOf(extra)),
                ),
            ),
            createResult = Result.success(extra),
        )
        val viewModel = modifierViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createModifierGroup("Extra", displayOrder = 1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, repository.getModifierGroupsCalls)
        assertEquals(ModifierGroupActionState.Success("Grup modifier berhasil dibuat"), viewModel.actionState.value)
        assertEquals(ModifierGroupListState.Success(listOf(extra)), viewModel.listState.value)
    }

    @Test
    fun createThenRefresh_preservesCreatedGroup() {
        val extra = modifierGroup(id = "group-extra", name = "Extra")
        val repository = FakeModifierRepository(
            getResults = ArrayDeque(
                listOf(
                    Result.success(emptyList()),
                    Result.success(listOf(extra)),
                    Result.success(listOf(extra)),
                ),
            ),
            createResult = Result.success(extra),
        )
        val viewModel = modifierViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createModifierGroup("Extra", displayOrder = 1)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.loadModifierGroups()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, repository.getModifierGroupsCalls)
        assertEquals(ModifierGroupListState.Success(listOf(extra)), viewModel.listState.value)
    }

    @Test
    fun duplicateCreate_mapsConflictToUsefulMessage() {
        val repository = FakeModifierRepository(
            getResults = ArrayDeque(listOf(Result.success(emptyList()))),
            createResult = Result.failure(
                httpException(
                    409,
                    "{\"error_code\":\"MODIFIER_GROUP_NAME_EXISTS\",\"message\":\"Modifier group name already exists in this tenant\"}",
                ),
            ),
        )
        val viewModel = modifierViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createModifierGroup("Extra", displayOrder = 1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModifierGroupActionState.Error("Nama grup modifier sudah digunakan."), viewModel.actionState.value)
        assertEquals(1, repository.getModifierGroupsCalls)
    }

    @Test
    fun validCreateSuccess_cannotBecomeActionErrorWhenAssignmentMetadataIsAbsent() {
        val globalGroup = modifierGroup(
            id = "group-extra",
            name = "Extra",
            required = false,
            selectionType = SelectionMode.SINGLE,
        )
        val repository = FakeModifierRepository(
            getResults = ArrayDeque(
                listOf(
                    Result.success(emptyList()),
                    Result.success(listOf(globalGroup)),
                ),
            ),
            createResult = Result.success(globalGroup),
        )
        val viewModel = modifierViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.createModifierGroup("Extra", displayOrder = 1)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.actionState.value !is ModifierGroupActionState.Error)
        assertEquals(ModifierGroupActionState.Success("Grup modifier berhasil dibuat"), viewModel.actionState.value)
    }

    private fun modifierViewModel(repository: ModifierRepository) = ModifierViewModel(
        getModifierGroupsUseCase = GetModifierGroupsUseCase(repository),
        createModifierGroupUseCase = CreateModifierGroupUseCase(repository),
        updateModifierGroupUseCase = UpdateModifierGroupUseCase(repository),
        deleteModifierGroupUseCase = DeleteModifierGroupUseCase(repository),
        createModifierOptionUseCase = CreateModifierOptionUseCase(repository),
        updateModifierOptionUseCase = UpdateModifierOptionUseCase(repository),
        deleteModifierOptionUseCase = DeleteModifierOptionUseCase(repository),
    )

    private class FakeModifierRepository(
        private val getResults: ArrayDeque<Result<List<ModifierGroup>>>,
        private val createResult: Result<ModifierGroup>,
    ) : ModifierRepository {
        var getModifierGroupsCalls = 0

        override suspend fun getModifierGroups(includeInactive: Boolean): Result<List<ModifierGroup>> {
            getModifierGroupsCalls += 1
            return getResults.removeFirst()
        }

        override suspend fun createModifierGroup(
            name: String,
            isActive: Boolean,
            displayOrder: Int,
        ): Result<ModifierGroup> = createResult

        override suspend fun getModifierGroup(groupId: String): Result<ModifierGroup> = error("Not used")
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
        ): Result<ModifierOption> = error("Not used")

        override suspend fun updateModifierOption(
            groupId: String,
            optionId: String,
            name: String?,
            priceDelta: Long?,
            isActive: Boolean?,
            displayOrder: Int?,
        ): Result<ModifierOption> = error("Not used")

        override suspend fun deleteModifierOption(groupId: String, optionId: String): Result<Unit> = error("Not used")
        override suspend fun getProductModifierGroups(productId: String): Result<List<ProductModifierAssignment>> = error("Not used")
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
        override suspend fun replaceModifierGroups(
            productId: String,
            assignments: List<ProductModifierAssignment>,
        ): Result<List<ProductModifierAssignment>> = error("Not used")
    }

    private fun modifierGroup(
        id: String,
        name: String,
        required: Boolean = false,
        selectionType: SelectionMode = SelectionMode.SINGLE,
    ) = ModifierGroup(
        id = id,
        tenantId = "tenant-id",
        name = name,
        isActive = true,
        displayOrder = 1,
        required = required,
        selectionType = selectionType,
        options = emptyList(),
    )

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            body.toResponseBody("application/json".toMediaType()),
        ),
    )
}
