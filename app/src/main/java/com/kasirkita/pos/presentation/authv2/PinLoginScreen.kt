package com.kasirkita.pos.presentation.authv2

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PinLoginScreen(
    state: AuthV2State.PinLogin,
    onLogin: (pin: String, deviceName: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var pin by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val deviceName = remember { androidDeviceName() }
    val submit = {
        focusManager.clearFocus()
        onLogin(pin, deviceName)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp),
            enabled = !state.isLoading,
        ) {
            Text("Kembali")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Masukkan PIN",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = state.user.name,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = state.user.role.displayName(),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { value ->
                pin = value.filter(Char::isDigit).take(PIN_LENGTH)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            label = { Text("PIN 6 digit") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (pin.length == PIN_LENGTH) submit() },
            ),
            singleLine = true,
        )
        state.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = submit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            enabled = pin.length == PIN_LENGTH && !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Memverifikasi PIN")
            } else {
                Text("Masuk")
            }
        }
    }
}

private fun androidDeviceName(): String? = listOf(Build.MANUFACTURER, Build.MODEL)
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinctBy { part -> part.lowercase() }
    .joinToString(" ")
    .take(MAX_DEVICE_NAME_LENGTH)
    .takeIf(String::isNotBlank)

private const val PIN_LENGTH = 6
private const val MAX_DEVICE_NAME_LENGTH = 100
