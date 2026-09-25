package com.kasirkita.pos.presentation.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
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
}

@Composable
private fun LoadingShiftContent() {
    ShiftContentColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator()
        Text(
            text = "Memuat shift...",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
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
        ShiftHeading(
            title = "Buka Shift",
            supportingText = "Masukkan kas awal sebelum mulai menerima transaksi.",
        )
        ShiftValue(label = "Outlet", value = outletName)
        OutlinedTextField(
            value = openingCashInput,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) {
                    openingCashInput = value
                    inputError = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Kas Awal") },
            prefix = { Text("Rp") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = inputError != null,
            supportingText = inputError?.let { error ->
                { Text(error) }
            },
            enabled = !isSubmitting,
        )
        Button(
            onClick = {
                when (val result = validateShiftCash(openingCashInput, "Kas awal")) {
                    is ShiftCashValidationResult.Valid -> onOpen(outletId, result.amount)
                    is ShiftCashValidationResult.Invalid -> inputError = result.message
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            enabled = !isSubmitting,
        ) {
            Text(if (isSubmitting) "Membuka Shift..." else "Mulai Shift")
        }
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
        ShiftHeading(
            title = "Shift Aktif",
            supportingText = "Periksa shift yang sedang berjalan sebelum menutupnya.",
        )
        ShiftValue(label = "Outlet", value = outletName)
        ShiftValue(label = "Kas awal", value = formatShiftMoney(shift.openingCash))
        ShiftValue(label = "Dibuka", value = formatShiftTime(shift.openedAt))
        ShiftValue(label = "Status", value = shift.status)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Tutup Shift",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Masukkan jumlah kas yang benar-benar ada di laci.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = closingCashInput,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) {
                    closingCashInput = value
                    inputError = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Kas Akhir") },
            prefix = { Text("Rp") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = inputError != null,
            supportingText = inputError?.let { error ->
                { Text(error) }
            },
            enabled = !isSubmitting,
        )
        Button(
            onClick = {
                when (val result = validateShiftCash(closingCashInput, "Kas akhir")) {
                    is ShiftCashValidationResult.Valid -> onClose(result.amount)
                    is ShiftCashValidationResult.Invalid -> inputError = result.message
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            enabled = !isSubmitting,
        ) {
            Text(if (isSubmitting) "Menutup Shift..." else "Tutup Shift")
        }
    }
}

@Composable
private fun ClosedShiftContent(
    shift: Shift,
    outletName: String,
    onStartNewShift: () -> Unit,
) {
    ShiftContentColumn {
        ShiftHeading(
            title = "Shift Ditutup",
            supportingText = "Rekonsiliasi kas sudah dihitung oleh server.",
        )
        ShiftValue(label = "Outlet", value = outletName)
        ShiftValue(label = "Kas awal", value = formatShiftMoney(shift.openingCash))
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
        ShiftValue(label = "Status", value = shift.status)
        Button(
            onClick = onStartNewShift,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Mulai Shift Baru")
        }
    }
}

@Composable
private fun ShiftErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    ShiftContentColumn {
        ShiftHeading(
            title = "Shift tidak dapat diproses",
            supportingText = message,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Coba Lagi")
        }
    }
}

@Composable
private fun ShiftContentColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
private fun ShiftHeading(
    title: String,
    supportingText: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = supportingText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ShiftValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun formatShiftMoney(amount: Long): String {
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
