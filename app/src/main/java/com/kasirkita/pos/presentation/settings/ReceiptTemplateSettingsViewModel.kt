package com.kasirkita.pos.presentation.settings

import com.kasirkita.pos.domain.model.ModifierSnapshot
import com.kasirkita.pos.domain.model.Payment
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptCashier
import com.kasirkita.pos.domain.model.ReceiptCustomer
import com.kasirkita.pos.domain.model.ReceiptFooterSettings
import com.kasirkita.pos.domain.model.ReceiptHeaderSettings
import com.kasirkita.pos.domain.model.ReceiptItem
import com.kasirkita.pos.domain.model.ReceiptOutlet
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptSettingsUpdate
import com.kasirkita.pos.domain.model.ReceiptTenant
import com.kasirkita.pos.domain.model.ReceiptVisibilitySettings
import com.kasirkita.pos.domain.repository.ReceiptSettingsRepository
import com.kasirkita.pos.domain.usecase.BuildReceiptDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable operational identity supplied by navigation; never editable in the form. */
data class ReceiptSettingsContext(
    val tenantId: String,
    val outletId: String?,
    val role: com.kasirkita.pos.domain.model.UserRole,
)

enum class ReceiptSettingsField(val label: String, val maxLength: Int?, val required: Boolean) {
    StoreName("Nama toko", 100, true),
    OutletName("Nama outlet", 100, true),
    Address("Alamat", 500, false),
    Phone("Telepon", 30, false),
    AdditionalHeaderText("Teks header tambahan", 500, false),
    ThankYouText("Teks terima kasih", 200, true),
    PromotionalFooterText("Teks promo footer", 500, false),
}

enum class ReceiptToggle(val label: String) {
    ShowSku("Tampilkan SKU"),
    ShowModifiers("Tampilkan modifier"),
    ShowItemNotes("Tampilkan catatan item"),
    ShowCashier("Tampilkan kasir"),
    ShowCustomer("Tampilkan pelanggan"),
}

data class ReceiptTemplateSettingsForm(
    val storeName: String = "",
    val outletName: String = "",
    val address: String = "",
    val phone: String = "",
    val additionalHeaderText: String = "",
    val thankYouText: String = "",
    val promotionalFooterText: String = "",
    val showSku: Boolean = true,
    val showModifiers: Boolean = true,
    val showItemNotes: Boolean = true,
    val showCashier: Boolean = true,
    val showCustomer: Boolean = true,
)

data class ReceiptTemplateFormErrors(
    val storeName: String? = null,
    val outletName: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val additionalHeaderText: String? = null,
    val thankYouText: String? = null,
    val promotionalFooterText: String? = null,
) {
    val hasAny: Boolean get() = listOf(
        storeName, outletName, address, phone, additionalHeaderText,
        thankYouText, promotionalFooterText,
    ).any { it != null }
}

@HiltViewModel
class ReceiptTemplateSettingsViewModel internal constructor(
    private val refreshSettings: suspend (String, String) -> Result<ReceiptSettings>,
    private val updateSettings: suspend (String, String, com.kasirkita.pos.domain.model.ReceiptSettingsUpdate) -> Result<ReceiptSettings>,
    private val buildReceiptDocument: BuildReceiptDocumentUseCase = BuildReceiptDocumentUseCase(),
) : ViewModel() {

    @Inject
    constructor(
        receiptSettingsRepository: ReceiptSettingsRepository,
    ) : this(
        refreshSettings = receiptSettingsRepository::refreshSettings,
        updateSettings = receiptSettingsRepository::updateSettings,
    )

    private val _state = MutableStateFlow<ReceiptTemplateSettingsState>(ReceiptTemplateSettingsState.Loading)
    val state: StateFlow<ReceiptTemplateSettingsState> = _state.asStateFlow()

    private var context: ReceiptSettingsContext? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private var saveJob: kotlinx.coroutines.Job? = null

    fun load(context: ReceiptSettingsContext) {
        this.context = context
        if (context.role == com.kasirkita.pos.domain.model.UserRole.CASHIER) {
            _state.value = ReceiptTemplateSettingsState.AccessDenied
            return
        }
        if (context.outletId.isNullOrBlank()) {
            _state.value = ReceiptTemplateSettingsState.MissingOutlet
            return
        }
        if (loadJob?.isActive == true) return
        val outletId = context.outletId
        _state.value = ReceiptTemplateSettingsState.Loading
        loadJob = viewModelScope.launch {
            refreshSettings(context.tenantId, outletId).fold(
                onSuccess = { settings -> _state.value = editingState(settings, settings) },
                onFailure = { error ->
                    _state.value = ReceiptTemplateSettingsState.Error(messageFor(error))
                },
            )
        }
    }

    fun retry() {
        val current = context ?: return
        load(current)
    }

    fun updateContext(context: ReceiptSettingsContext) {
        loadJob?.cancel()
        loadJob = null
        saveJob?.cancel()
        saveJob = null
        load(context)
    }

    fun updateField(field: ReceiptSettingsField, value: String) {
        val editing = _state.value as? ReceiptTemplateSettingsState.Editing ?: return
        _state.value = editing.withForm(editing.form.withField(field, value))
    }

    fun updateToggle(toggle: ReceiptToggle, value: Boolean) {
        val editing = _state.value as? ReceiptTemplateSettingsState.Editing ?: return
        _state.value = editing.withForm(editing.form.withToggle(toggle, value))
    }

    fun selectPaperWidth(widthMm: Int) {
        val editing = _state.value as? ReceiptTemplateSettingsState.Editing ?: return
        _state.value = editing.copy(paperWidthMm = widthMm, preview = previewFor(editing.form, widthMm))
    }

    fun save() {
        val editing = _state.value as? ReceiptTemplateSettingsState.Editing ?: return
        if (!editing.canSave || saveJob?.isActive == true) return
        val current = context ?: return
        val outletId = current.outletId?.takeIf(String::isNotBlank) ?: return
        val update = editing.form.toUpdate()
        _state.value = editing.copy(isSaving = true, canSave = false, saveError = null)
        saveJob = viewModelScope.launch {
            try {
                updateSettings(current.tenantId, outletId, update).fold(
                    onSuccess = { canonical ->
                        _state.value = editingState(canonical, canonical).copy(savedConfirmation = true)
                    },
                    onFailure = { error ->
                        val latest = _state.value as? ReceiptTemplateSettingsState.Editing ?: return@fold
                        _state.value = latest.copy(
                            isSaving = false,
                            saveError = messageFor(error),
                            savedConfirmation = false,
                        )
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun acknowledgeSavedConfirmation() {
        val editing = _state.value as? ReceiptTemplateSettingsState.Editing ?: return
        _state.value = editing.copy(savedConfirmation = false)
    }

    private fun editingState(
        settings: ReceiptSettings,
        baseline: ReceiptSettings,
    ): ReceiptTemplateSettingsState.Editing {
        val form = settings.toForm()
        return ReceiptTemplateSettingsState.Editing(
            form = form,
            baseline = baseline.toForm(),
            preview = previewFor(form, 58),
        )
    }

    private fun previewFor(form: ReceiptTemplateSettingsForm, widthMm: Int) =
        buildReceiptDocument(ReceiptPreviewSample.receipt, form.toSettings(), widthMm)

    private fun messageFor(error: Throwable): String = when (error) {
        is com.kasirkita.pos.domain.error.ReceiptSettingsError -> when {
            error.httpCode == 400 -> "Data template struk tidak valid."
            error.httpCode == 401 -> "Sesi berakhir. Silakan masuk kembali."
            error.httpCode == 403 -> "Anda tidak memiliki akses ke template struk."
            error.httpCode == 404 -> "Pengaturan template struk tidak ditemukan."
            error.errorCode == "NETWORK_UNAVAILABLE" -> "Tidak ada koneksi. Coba lagi."
            error.errorCode == "CACHE_MISS" -> "Pengaturan struk belum tersedia."
            error.errorCode == "CONTRACT_MISMATCH" -> "Data struk tidak cocok dengan outlet ini."
            else -> "Gagal menyimpan template struk. Coba lagi."
        }
        else -> "Gagal menyimpan template struk. Coba lagi."
    }

    private fun ReceiptTemplateSettingsState.Editing.withForm(updated: ReceiptTemplateSettingsForm):
        ReceiptTemplateSettingsState.Editing {
        val errors = validate(updated)
        return copy(
            form = updated,
            errors = errors,
            isDirty = updated != baseline,
            canSave = updated != baseline && !errors.hasAny && !isSaving,
            savedConfirmation = false,
            preview = previewFor(updated, paperWidthMm),
        )
    }

    private fun validate(form: ReceiptTemplateSettingsForm) = ReceiptTemplateFormErrors(
        storeName = validateField(form.storeName, ReceiptSettingsField.StoreName),
        outletName = validateField(form.outletName, ReceiptSettingsField.OutletName),
        address = validateField(form.address, ReceiptSettingsField.Address),
        phone = validateField(form.phone, ReceiptSettingsField.Phone),
        additionalHeaderText = validateField(form.additionalHeaderText, ReceiptSettingsField.AdditionalHeaderText),
        thankYouText = validateField(form.thankYouText, ReceiptSettingsField.ThankYouText),
        promotionalFooterText = validateField(form.promotionalFooterText, ReceiptSettingsField.PromotionalFooterText),
    )

    private fun validateField(value: String, field: ReceiptSettingsField): String? {
        val max = field.maxLength ?: return null
        if (value.length > max) return "Maksimal $max karakter."
        if (field.required && value.isBlank()) return "Wajib diisi."
        return null
    }
}

sealed interface ReceiptTemplateSettingsState {
    data object Loading : ReceiptTemplateSettingsState
    data object MissingOutlet : ReceiptTemplateSettingsState
    data object AccessDenied : ReceiptTemplateSettingsState
    data class Error(val message: String) : ReceiptTemplateSettingsState

    data class Editing(
        val form: ReceiptTemplateSettingsForm,
        val baseline: ReceiptTemplateSettingsForm,
        val errors: ReceiptTemplateFormErrors = ReceiptTemplateFormErrors(),
        val isDirty: Boolean = false,
        val canSave: Boolean = false,
        val isSaving: Boolean = false,
        val savedConfirmation: Boolean = false,
        val saveError: String? = null,
        val paperWidthMm: Int = 58,
        val preview: com.kasirkita.pos.domain.model.ReceiptDocument,
    ) : ReceiptTemplateSettingsState
}

private fun ReceiptSettings.toForm() = ReceiptTemplateSettingsForm(
    storeName = header.storeName,
    outletName = header.outletName,
    address = header.address.orEmpty(),
    phone = header.phone.orEmpty(),
    additionalHeaderText = header.additionalText.orEmpty(),
    thankYouText = footer.thankYouText,
    promotionalFooterText = footer.promoText.orEmpty(),
    showSku = visibility.showSku,
    showModifiers = visibility.showModifiers,
    showItemNotes = visibility.showItemNotes,
    showCashier = visibility.showCashier,
    showCustomer = visibility.showCustomer,
)

private fun ReceiptTemplateSettingsForm.toUpdate() = ReceiptSettingsUpdate(
    headerStoreName = storeName.trim(),
    headerOutletName = outletName.trim(),
    headerAddress = nullableTrim(address),
    headerPhone = nullableTrim(phone),
    headerAdditionalText = nullableTrim(additionalHeaderText),
    showSku = showSku,
    showModifiers = showModifiers,
    showItemNotes = showItemNotes,
    showCashier = showCashier,
    showCustomer = showCustomer,
    footerThankYouText = thankYouText.trim(),
    footerPromoText = nullableTrim(promotionalFooterText),
)

private fun ReceiptTemplateSettingsForm.toSettings() = ReceiptSettings(
    tenantId = "preview-tenant",
    outletId = "preview-outlet",
    header = ReceiptHeaderSettings(
        storeName = storeName.trim(),
        outletName = outletName.trim(),
        address = nullableTrim(address),
        phone = nullableTrim(phone),
        additionalText = nullableTrim(additionalHeaderText),
    ),
    visibility = ReceiptVisibilitySettings(
        showSku = showSku,
        showModifiers = showModifiers,
        showItemNotes = showItemNotes,
        showCashier = showCashier,
        showCustomer = showCustomer,
    ),
    footer = ReceiptFooterSettings(
        thankYouText = thankYouText.trim(),
        promoText = nullableTrim(promotionalFooterText),
    ),
    templateVersion = 1,
    createdAt = null,
    updatedAt = null,
)

private fun nullableTrim(value: String): String? = value.trim().takeIf(String::isNotEmpty)

private fun ReceiptTemplateSettingsForm.withField(
    field: ReceiptSettingsField,
    value: String,
): ReceiptTemplateSettingsForm = when (field) {
    ReceiptSettingsField.StoreName -> copy(storeName = value)
    ReceiptSettingsField.OutletName -> copy(outletName = value)
    ReceiptSettingsField.Address -> copy(address = value)
    ReceiptSettingsField.Phone -> copy(phone = value)
    ReceiptSettingsField.AdditionalHeaderText -> copy(additionalHeaderText = value)
    ReceiptSettingsField.ThankYouText -> copy(thankYouText = value)
    ReceiptSettingsField.PromotionalFooterText -> copy(promotionalFooterText = value)
}

private fun ReceiptTemplateSettingsForm.withToggle(
    toggle: ReceiptToggle,
    value: Boolean,
): ReceiptTemplateSettingsForm = when (toggle) {
    ReceiptToggle.ShowSku -> copy(showSku = value)
    ReceiptToggle.ShowModifiers -> copy(showModifiers = value)
    ReceiptToggle.ShowItemNotes -> copy(showItemNotes = value)
    ReceiptToggle.ShowCashier -> copy(showCashier = value)
    ReceiptToggle.ShowCustomer -> copy(showCustomer = value)
}

private object ReceiptPreviewSample {
    val receipt = Receipt(
        transactionId = "PREVIEW-001",
        clientTransactionId = "PREVIEW-CLIENT-001",
        status = "completed",
        createdAt = "2026-10-05T15:14:00Z",
        tenant = ReceiptTenant("preview-tenant", "Toko Preview", "Jl. Preview 1"),
        outlet = ReceiptOutlet("preview-outlet", "Outlet Preview", "Jl. Outlet 1"),
        cashier = ReceiptCashier("cashier-preview", "Tuti"),
        customer = ReceiptCustomer("customer-preview", "Budi", "+628100000", null),
        items = listOf(
            ReceiptItem(
                id = "item-1",
                productId = "product-1",
                productName = "Kopi Susu Gula Aren",
                sku = "KSGA-001",
                quantity = 2,
                unitPrice = 18_000,
                subtotal = 36_000,
                productNameSnapshot = "Kopi Susu Gula Aren",
                skuSnapshot = "KSGA-001",
                note = "Less ice",
                modifierSnapshots = listOf(
                    ModifierSnapshot("mod-1", "group-1", "option-1", "Size", "Large", 3_000),
                ),
            ),
            ReceiptItem(
                id = "item-2",
                productId = "product-2",
                productName = "Roti Cokelat",
                sku = "ROTI-9",
                quantity = 1,
                unitPrice = 12_000,
                subtotal = 12_000,
                productNameSnapshot = "Roti Cokelat",
                skuSnapshot = "ROTI-9",
            ),
        ),
        payment = Payment(
            id = "payment-preview",
            method = "CASH",
            status = "settled",
            amount = 49_500,
            paidAt = "2026-10-05T15:14:30Z",
            amountReceived = 50_000,
            changeAmount = 500,
        ),
        subtotal = 48_000,
        discount = 3_000,
        tax = 4_500,
        total = 49_500,
        change = 500,
    )
}