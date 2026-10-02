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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.theme.KasirSpacing

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
            .padding(horizontal = KasirSpacing.ScreenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        KasirTextButton(
            text = "Kembali",
            onClick = onBack,
            enabled = !state.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Masukkan PIN",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        KasirCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Masuk sebagai",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.user.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = state.user.role.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { value ->
                pin = value.filter(Char::isDigit).take(PIN_LENGTH)
            },
            label = { Text("PIN 6 digit") },
                shape = RoundedCornerShape(KasirSpacing.CornerRadius),
                keyboardActions = KeyboardActions(onDone = { if (pin.length == PIN_LENGTH) submit() }),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
        )

        state.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        KasirPrimaryButton(
            text = "Masuk",
            onClick = submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length == PIN_LENGTH,
            isLoading = state.isLoading,
        )
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
