package com.kasirkita.pos.presentation.modifier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.usecase.CreateModifierGroupUseCase
import com.kasirkita.pos.domain.usecase.CreateModifierOptionUseCase
import com.kasirkita.pos.domain.usecase.DeleteModifierGroupUseCase
import com.kasirkita.pos.domain.usecase.DeleteModifierOptionUseCase
import com.kasirkita.pos.domain.usecase.GetModifierGroupsUseCase
import com.kasirkita.pos.domain.usecase.UpdateModifierGroupUseCase
import com.kasirkita.pos.domain.usecase.UpdateModifierOptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModifierViewModel @Inject constructor(
    private val getModifierGroupsUseCase: GetModifierGroupsUseCase,
    private val createModifierGroupUseCase: CreateModifierGroupUseCase,
    private val updateModifierGroupUseCase: UpdateModifierGroupUseCase,
    private val deleteModifierGroupUseCase: DeleteModifierGroupUseCase,
    private val createModifierOptionUseCase: CreateModifierOptionUseCase,
    private val updateModifierOptionUseCase: UpdateModifierOptionUseCase,
    private val deleteModifierOptionUseCase: DeleteModifierOptionUseCase,
) : ViewModel() {

    private val _listState = MutableStateFlow<ModifierGroupListState>(ModifierGroupListState.Loading)
    val listState: StateFlow<ModifierGroupListState> = _listState.asStateFlow()

    private val _actionState = MutableStateFlow<ModifierGroupActionState>(ModifierGroupActionState.Idle)
    val actionState: StateFlow<ModifierGroupActionState> = _actionState.asStateFlow()

    init {
        loadModifierGroups()
    }

    fun loadModifierGroups(includeInactive: Boolean = true) {
        viewModelScope.launch {
            _listState.value = ModifierGroupListState.Loading
            getModifierGroupsUseCase(includeInactive).fold(
                onSuccess = { groups ->
                    _listState.value = ModifierGroupListState.Success(groups)
                },
                onFailure = { throwable ->
                    _listState.value = ModifierGroupListState.Error(
                        throwable.message ?: "Gagal memuat grup modifier"
                    )
                }
            )
        }
    }

    fun createModifierGroup(name: String, displayOrder: Int = 0) {
        viewModelScope.launch {
            _actionState.value = ModifierGroupActionState.Loading
            createModifierGroupUseCase(name = name, displayOrder = displayOrder).fold(
                onSuccess = {
                    _actionState.value = ModifierGroupActionState.Success("Grup modifier berhasil dibuat")
                    loadModifierGroups()
                },
                onFailure = { throwable ->
                    _actionState.value = ModifierGroupActionState.Error(
                        throwable.message ?: "Gagal membuat grup modifier"
                    )
                }
            )
        }
    }

    fun updateModifierGroup(groupId: String, name: String?, isActive: Boolean?, displayOrder: Int?) {
        viewModelScope.launch {
            _actionState.value = ModifierGroupActionState.Loading
            updateModifierGroupUseCase(
                groupId = groupId,
                name = name,
                isActive = isActive,
                displayOrder = displayOrder
            ).fold(
                onSuccess = {
                    _actionState.value = ModifierGroupActionState.Success("Grup modifier berhasil diperbarui")
                    loadModifierGroups()
                },
                onFailure = { throwable ->
                    _actionState.value = ModifierGroupActionState.Error(
                        throwable.message ?: "Gagal memperbarui grup modifier"
                    )
                }
            )
        }
    }

    fun deleteModifierGroup(groupId: String) {
        viewModelScope.launch {
            _actionState.value = ModifierGroupActionState.Loading
            deleteModifierGroupUseCase(groupId).fold(
                onSuccess = {
                    _actionState.value = ModifierGroupActionState.Success("Grup modifier berhasil dihapus")
                    loadModifierGroups()
                },
                onFailure = { throwable ->
                    _actionState.value = ModifierGroupActionState.Error(
                        throwable.message ?: "Gagal menghapus grup modifier"
                    )
                }
            )
        }
    }

    fun createOption(groupId: String, name: String, priceDelta: Long, displayOrder: Int = 0) {
        viewModelScope.launch {
            _actionState.value = ModifierGroupActionState.Loading
            createModifierOptionUseCase(
                groupId = groupId,
                name = name,
                priceDelta = priceDelta,
                displayOrder = displayOrder
            ).fold(
                onSuccess = {
                    _actionState.value = ModifierGroupActionState.Success("Pilihan modifier berhasil ditambahkan")
                    loadModifierGroups()
                },
                onFailure = { throwable ->
                    _actionState.value = ModifierGroupActionState.Error(
                        throwable.message ?: "Gagal menambahkan pilihan modifier"
                    )
                }
            )
        }
    }

    fun updateOption(
        groupId: String,
        optionId: String,
        name: String?,
        priceDelta: Long?,
        isActive: Boolean?,
        displayOrder: Int?
    ) {
        viewModelScope.launch {
            _actionState.value = ModifierGroupActionState.Loading
            updateModifierOptionUseCase(
                groupId = groupId,
                optionId = optionId,
                name = name,
                priceDelta = priceDelta,
                isActive = isActive,
                displayOrder = displayOrder
            ).fold(
                onSuccess = {
                    _actionState.value = ModifierGroupActionState.Success("Pilihan modifier berhasil diperbarui")
                    loadModifierGroups()
                },
                onFailure = { throwable ->
                    _actionState.value = ModifierGroupActionState.Error(
                        throwable.message ?: "Gagal memperbarui pilihan modifier"
                    )
                }
            )
        }
    }

    fun deleteOption(groupId: String, optionId: String) {
        viewModelScope.launch {
            _actionState.value = ModifierGroupActionState.Loading
            deleteModifierOptionUseCase(groupId, optionId).fold(
                onSuccess = {
                    _actionState.value = ModifierGroupActionState.Success("Pilihan modifier berhasil dihapus")
                    loadModifierGroups()
                },
                onFailure = { throwable ->
                    _actionState.value = ModifierGroupActionState.Error(
                        throwable.message ?: "Gagal menghapus pilihan modifier"
                    )
                }
            )
        }
    }

    fun resetActionState() {
        _actionState.value = ModifierGroupActionState.Idle
    }
}