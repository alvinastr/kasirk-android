package com.kasirkita.pos.presentation.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kasirkita.pos.domain.model.Shift
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ShiftScreen(
    outletId: String,
    onShiftOpen: () -> Unit = {},
    viewModel: ShiftViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val loadedShift = (state as? ShiftState.ShiftLoaded)?.shift
        if (loadedShift?.status.equals("OPEN", ignoreCase = true)) {
            onShiftOpen()
        }
    }

    when (val currentState = state) {
        ShiftState.Loading -> LoadingContent()
        ShiftState.NoShift -> NoShiftContent(
            outletId = outletId,
            onOpenShift = viewModel::openShift,
        )
        is ShiftState.ShiftLoaded -> ActiveShiftContent(
            shift = currentState.shift,
            onCloseShift = viewModel::closeShift,
        )
        is ShiftState.Error -> ErrorContent(
            message = currentState.message,
            onRetry = viewModel::loadCurrentShift,
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoShiftContent(
    outletId: String,
    onOpenShift: (String, Long) -> Unit,
) {
    var openingCashInput by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    ShiftFormContainer {
        Text(
            text = "Belum ada shift aktif",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = openingCashInput,
            onValueChange = { value ->
                openingCashInput = value.filter(Char::isDigit)
                inputError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Opening Cash") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = inputError != null,
            supportingText = inputError?.let { message ->
                { Text(message) }
            },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val openingCash = openingCashInput.toLongOrNull()
                if (openingCash == null) {
                    inputError = "Opening Cash tidak valid"
                } else {
                    onOpenShift(outletId, openingCash)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Mulai Shift")
        }
    }
}

@Composable
private fun ActiveShiftContent(
    shift: Shift,
    onCloseShift: (Long) -> Unit,
) {
    var closingCashInput by rememberSaveable(shift.id) { mutableStateOf("") }
    var inputError by rememberSaveable(shift.id) { mutableStateOf<String?>(null) }
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    ShiftFormContainer {
        Text(
            text = "Status ${shift.status}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Opening Cash: Rp${numberFormat.format(shift.openingCash)}")
        Text("Opened At: ${shift.openedAt}")
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = closingCashInput,
            onValueChange = { value ->
                closingCashInput = value.filter(Char::isDigit)
                inputError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Closing Cash") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = inputError != null,
            supportingText = inputError?.let { message ->
                { Text(message) }
            },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val closingCash = closingCashInput.toLongOrNull()
                if (closingCash == null) {
                    inputError = "Closing Cash tidak valid"
                } else {
                    onCloseShift(closingCash)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Tutup Shift")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    ShiftFormContainer {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Coba lagi")
        }
    }
}

@Composable
private fun ShiftFormContainer(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
