package com.kasirkita.pos.presentation.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.ui.components.StatusBadge
import com.kasirkita.pos.ui.components.StatusBadgeTone
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirTextField
import com.kasirkita.pos.ui.theme.KasirSpacing
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ShiftScreen(
    outletId: String,
    outletName: String = outletId,
    autoNavigateToHome: Boolean = true,
    onShiftOpen: () -> Unit = {},
    viewModel: ShiftViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val loadedShift = (state as? ShiftState.ShiftLoaded)?.shift
    val shouldNavigateToHome = shouldNavigateToHomeFromShift(
        autoNavigateToHome = autoNavigateToHome,
        selectedOutletId = outletId,
        shift = loadedShift,
    )
    LaunchedEffect(shouldNavigateToHome) {
        if (shouldNavigateToHome) {
            onShiftOpen()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.shiftOpened.collect {
            onShiftOpen()
        }
    }

    ShiftManagementContent(
        state = state,
        outletId = outletId,
        outletName = outletName,
        onOpen = viewModel::openShift,
        onClose = viewModel::closeShift,
        onRetry = viewModel::loadCurrentShift,
        onStartNewShift = viewModel::startNewShift,
    )
}

@Composable
internal fun ShiftManagementContent(
    state: ShiftState,
    outletId: String,
    outletName: String,
    onOpen: (String, Long) -> Unit,
    onClose: (Long) -> Unit,
    onRetry: () -> Unit,
    onStartNewShift: () -> Unit,
) {
    when (state) {
        ShiftState.Loading -> LoadingShiftContent()
        ShiftState.NoShift -> OpenShiftContent(
            outletId = outletId,
            outletName = outletName,
            isSubmitting = false,
            onOpen = onOpen,
        )

        ShiftState.Opening -> OpenShiftContent(
            outletId = outletId,
            outletName = outletName,
            isSubmitting = true,
            onOpen = onOpen,
        )

        is ShiftState.ShiftLoaded -> ActiveShiftContent(
            shift = state.shift,
            outletName = outletName,
            isSubmitting = false,
            onClose = onClose,
        )

        is ShiftState.Closing -> ActiveShiftContent(
            shift = state.shift,
            outletName = outletName,
            isSubmitting = true,
            onClose = onClose,
        )

        is ShiftState.ShiftClosed -> ClosedShiftContent(
            shift = state.shift,
            outletName = outletName,
            onStartNewShift = onStartNewShift,
        )

        is ShiftState.Error -> ShiftErrorContent(
            message = state.message,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun LoadingShiftContent() {
    KasirLoadingState(
        message = "Memuat shift...",
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OpenShiftContent(
    outletId: String,
    outletName: String,
    isSubmitting: Boolean,
    onOpen: (String, Long) -> Unit,
) {
    var openingCashInput by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    ShiftContentColumn {
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Shift belum dibuka",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Masukkan kas awal sebelum mulai menerima transaksi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        ShiftValue(label = "Outlet", value = outletName)

        KasirTextField(
            value = openingCashInput,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) {
                    openingCashInput = value
                    inputError = null
                }
            },
            label = "Kas Awal",
            modifier = Modifier.fillMaxWidth(),
            prefix = "Rp",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = inputError != null,
            supportingText = inputError,
            enabled = !isSubmitting,
        )

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        KasirPrimaryButton(
            text = "Buka Shift",
            onClick = {
                when (val result = validateShiftCash(openingCashInput, "Kas awal")) {
                    is ShiftCashValidationResult.Valid -> onOpen(outletId, result.amount)
                    is ShiftCashValidationResult.Invalid -> inputError = result.message
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            isLoading = isSubmitting,
        )
    }
}

@Composable
private fun ActiveShiftContent(
    shift: Shift,
    outletName: String,
    isSubmitting: Boolean,
    onClose: (Long) -> Unit,
) {
    var closingCashInput by rememberSaveable(shift.id) { mutableStateOf("") }
    var inputError by rememberSaveable(shift.id) { mutableStateOf<String?>(null) }

    ShiftContentColumn {
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Shift Aktif",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Periksa shift yang sedang berjalan sebelum menutupnya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        ShiftValue(label = "Outlet", value = outletName)
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text("Kas awal", style = MaterialTheme.typography.labelMedium)
            PriceText(text = formatShiftMoney(shift.openingCash))
        }
        ShiftValue(label = "Dibuka", value = formatShiftTime(shift.openedAt))
        StatusBadge(
            text = when {
                shift.status.equals("OPEN", ignoreCase = true) -> "Shift aktif"
                shift.status.equals("CLOSED", ignoreCase = true) -> "Shift ditutup"
                else -> shift.status
            },
            tone = if (shift.status.equals("OPEN", ignoreCase = true)) StatusBadgeTone.Success else StatusBadgeTone.Neutral,
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tutup Shift",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Masukkan jumlah kas yang benar-benar ada di laci.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        KasirTextField(
            value = closingCashInput,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) {
                    closingCashInput = value
                    inputError = null
                }
            },
            label = "Kas Akhir",
            modifier = Modifier.fillMaxWidth(),
            prefix = "Rp",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = inputError != null,
            supportingText = inputError,
            enabled = !isSubmitting,
        )

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        KasirPrimaryButton(
            text = "Tutup Shift",
            onClick = {
                when (val result = validateShiftCash(closingCashInput, "Kas akhir")) {
                    is ShiftCashValidationResult.Valid -> onClose(result.amount)
                    is ShiftCashValidationResult.Invalid -> inputError = result.message
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            isLoading = isSubmitting,
        )
    }
}

@Composable
private fun ClosedShiftContent(
    shift: Shift,
    outletName: String,
    onStartNewShift: () -> Unit,
) {
    ShiftContentColumn {
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Shift Ditutup",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Periksa ringkasan kas sebelum membuka shift baru.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(KasirSpacing.ItemGap))

        ShiftValue(label = "Outlet", value = outletName)
        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text("Kas awal", style = MaterialTheme.typography.labelMedium)
            PriceText(text = formatShiftMoney(shift.openingCash))
        }
        ShiftValue(
            label = "Kas yang diharapkan",
            value = shift.expectedCash?.let(::formatShiftMoney) ?: "Belum tersedia",
        )
        ShiftValue(
            label = "Kas akhir",
            value = shift.closingCash?.let(::formatShiftMoney) ?: "Belum tersedia",
        )
        ShiftValue(
            label = "Selisih",
            value = shift.difference?.let(::formatShiftMoney) ?: "Belum tersedia",
        )
        StatusBadge(
            text = when {
                shift.status.equals("OPEN", ignoreCase = true) -> "Shift aktif"
                shift.status.equals("CLOSED", ignoreCase = true) -> "Shift ditutup"
                else -> shift.status
            },
            tone = if (shift.status.equals("OPEN", ignoreCase = true)) StatusBadgeTone.Success else StatusBadgeTone.Neutral,
        )

        Spacer(modifier = Modifier.height(24.dp))

        KasirPrimaryButton(
            text = "Mulai Shift Baru",
            onClick = onStartNewShift,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShiftErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    KasirErrorState(
        message = message,
        onRetry = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ShiftContentColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KasirSpacing.ScreenPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
private fun ShiftValue(
    label: String,
    value: String,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

internal fun formatShiftMoney(amount: Long?): String {
    if (amount == null) return "—"
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    return if (amount < 0) {
        "-Rp${formatter.format(-amount)}"
    } else {
        "Rp${formatter.format(amount)}"
    }
}

internal fun formatShiftTime(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofPattern(
                "dd MMM yyyy, HH.mm",
                Locale.forLanguageTag("id-ID"),
            ),
        )
}.getOrDefault(value)

internal fun shouldNavigateToHomeFromShift(
    autoNavigateToHome: Boolean,
    selectedOutletId: String,
    shift: Shift?,
): Boolean = autoNavigateToHome && shift?.let { activeShift ->
    activeShift.status.equals("OPEN", ignoreCase = true) &&
        activeShift.outletId == selectedOutletId
} == true
