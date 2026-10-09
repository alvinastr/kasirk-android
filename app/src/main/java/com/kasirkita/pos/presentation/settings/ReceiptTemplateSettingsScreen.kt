package com.kasirkita.pos.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.presentation.receipt.ReceiptDocumentPreview
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTextField
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.sectionTitle
import com.kasirkita.pos.ui.theme.supporting

@Composable
fun ReceiptTemplateSettingsScreen(
    context: ReceiptSettingsContext,
    onBack: () -> Unit,
    viewModel: ReceiptTemplateSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(context) {
        viewModel.updateContext(context)
    }

    fun requestBack() {
        val editing = state as? ReceiptTemplateSettingsState.Editing
        if (editing?.isDirty == true && !editing.isSaving) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::requestBack)

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Buang perubahan?") },
            text = { Text("Perubahan template struk belum disimpan.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Buang") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Lanjut edit") }
            },
        )
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(KasirSpacing.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Template Struk",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Atur isi header, footer, dan visibilitas untuk preview 58/80 mm.",
                        style = MaterialTheme.typography.supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KasirSecondaryButton(text = "Kembali", onClick = ::requestBack)
            }

            when (val current = state) {
                ReceiptTemplateSettingsState.Loading -> KasirLoadingState("Memuat template struk...")
                ReceiptTemplateSettingsState.AccessDenied -> KasirErrorState("Anda tidak memiliki akses ke template struk.")
                ReceiptTemplateSettingsState.MissingOutlet -> KasirErrorState("Pilih outlet aktif sebelum mengubah template struk.")
                is ReceiptTemplateSettingsState.Error -> KasirErrorState(
                    message = current.message,
                    onRetry = viewModel::retry,
                )
                is ReceiptTemplateSettingsState.Editing -> ReceiptTemplateEditor(
                    state = current,
                    onFieldChange = viewModel::updateField,
                    onToggleChange = viewModel::updateToggle,
                    onPaperWidthSelected = viewModel::selectPaperWidth,
                    onSave = viewModel::save,
                    onAcknowledgeSaved = viewModel::acknowledgeSavedConfirmation,
                )
            }
        }
    }
}

@Composable
private fun ReceiptTemplateEditor(
    state: ReceiptTemplateSettingsState.Editing,
    onFieldChange: (ReceiptSettingsField, String) -> Unit,
    onToggleChange: (ReceiptToggle, Boolean) -> Unit,
    onPaperWidthSelected: (Int) -> Unit,
    onSave: () -> Unit,
    onAcknowledgeSaved: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
    ) {
        state.saveError?.let { message ->
            KasirCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.savedConfirmation) {
            KasirCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Template struk tersimpan.", color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onAcknowledgeSaved) { Text("Tutup") }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 840.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
                ) {
                    EditorForm(
                        state = state,
                        onFieldChange = onFieldChange,
                        onToggleChange = onToggleChange,
                        modifier = Modifier.weight(1f),
                    )
                    PreviewCard(
                        document = state.preview,
                        paperWidthMm = state.paperWidthMm,
                        onPaperWidthSelected = onPaperWidthSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(KasirSpacing.SectionGap),
                ) {
                    EditorForm(
                        state = state,
                        onFieldChange = onFieldChange,
                        onToggleChange = onToggleChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PreviewCard(
                        document = state.preview,
                        paperWidthMm = state.paperWidthMm,
                        onPaperWidthSelected = onPaperWidthSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (state.isDirty) "Ada perubahan belum disimpan." else "Tidak ada perubahan.",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KasirPrimaryButton(
                text = if (state.isSaving) "Menyimpan..." else "Simpan Template",
                onClick = onSave,
                enabled = state.canSave,
                isLoading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(KasirSpacing.SectionGap))
    }
}

@Composable
private fun EditorForm(
    state: ReceiptTemplateSettingsState.Editing,
    onFieldChange: (ReceiptSettingsField, String) -> Unit,
    onToggleChange: (ReceiptToggle, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    KasirCard(modifier = modifier) {
        Text(text = "Field Template", style = MaterialTheme.typography.sectionTitle)
        FieldInput(
            label = ReceiptSettingsField.StoreName.label,
            value = state.form.storeName,
            error = state.errors.storeName,
            onValueChange = { onFieldChange(ReceiptSettingsField.StoreName, it) },
        )
        FieldInput(
            label = ReceiptSettingsField.OutletName.label,
            value = state.form.outletName,
            error = state.errors.outletName,
            onValueChange = { onFieldChange(ReceiptSettingsField.OutletName, it) },
        )
        FieldInput(
            label = ReceiptSettingsField.Address.label,
            value = state.form.address,
            error = state.errors.address,
            onValueChange = { onFieldChange(ReceiptSettingsField.Address, it) },
            singleLine = false,
        )
        FieldInput(
            label = ReceiptSettingsField.Phone.label,
            value = state.form.phone,
            error = state.errors.phone,
            onValueChange = { onFieldChange(ReceiptSettingsField.Phone, it) },
        )
        FieldInput(
            label = ReceiptSettingsField.AdditionalHeaderText.label,
            value = state.form.additionalHeaderText,
            error = state.errors.additionalHeaderText,
            onValueChange = { onFieldChange(ReceiptSettingsField.AdditionalHeaderText, it) },
            singleLine = false,
        )
        FieldInput(
            label = ReceiptSettingsField.ThankYouText.label,
            value = state.form.thankYouText,
            error = state.errors.thankYouText,
            onValueChange = { onFieldChange(ReceiptSettingsField.ThankYouText, it) },
        )
        FieldInput(
            label = ReceiptSettingsField.PromotionalFooterText.label,
            value = state.form.promotionalFooterText,
            error = state.errors.promotionalFooterText,
            onValueChange = { onFieldChange(ReceiptSettingsField.PromotionalFooterText, it) },
            singleLine = false,
        )

        Text(text = "Visibilitas", style = MaterialTheme.typography.sectionTitle)
        ToggleRow(ReceiptToggle.ShowSku.label, state.form.showSku) { onToggleChange(ReceiptToggle.ShowSku, it) }
        ToggleRow(ReceiptToggle.ShowModifiers.label, state.form.showModifiers) { onToggleChange(ReceiptToggle.ShowModifiers, it) }
        ToggleRow(ReceiptToggle.ShowItemNotes.label, state.form.showItemNotes) { onToggleChange(ReceiptToggle.ShowItemNotes, it) }
        ToggleRow(ReceiptToggle.ShowCashier.label, state.form.showCashier) { onToggleChange(ReceiptToggle.ShowCashier, it) }
        ToggleRow(ReceiptToggle.ShowCustomer.label, state.form.showCustomer) { onToggleChange(ReceiptToggle.ShowCustomer, it) }
    }
}

@Composable
private fun FieldInput(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    KasirTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isError = error != null,
        supportingText = error,
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreviewCard(
    document: ReceiptDocument,
    paperWidthMm: Int,
    onPaperWidthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    KasirCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Preview Struk", style = MaterialTheme.typography.sectionTitle)
                Text(
                    text = "${document.characterWidth} karakter, ${document.paperWidthMm} mm",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidthButton("58 mm", paperWidthMm == 58) { onPaperWidthSelected(58) }
                WidthButton("80 mm", paperWidthMm == 80) { onPaperWidthSelected(80) }
            }
        }
        ReceiptDocumentPreview(document = document)
    }
}

@Composable
private fun WidthButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
    }
}
