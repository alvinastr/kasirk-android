package com.kasirkita.pos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kasirkita.pos.ui.theme.KasirOnSuccessContainer
import com.kasirkita.pos.ui.theme.KasirOnWarningContainer
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.KasirSuccessContainer
import com.kasirkita.pos.ui.theme.KasirWarningContainer
import com.kasirkita.pos.ui.theme.price
import com.kasirkita.pos.ui.theme.sectionTitle
import com.kasirkita.pos.ui.theme.supporting

@Composable
fun KasirPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = KasirSpacing.ButtonMinHeight),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(KasirSpacing.CornerRadius),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text)
        }
    }
}

@Composable
fun KasirSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KasirSpacing.ButtonMinHeight),
        enabled = enabled,
        shape = RoundedCornerShape(KasirSpacing.CornerRadius),
        colors = if (destructive) {
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        border = BorderStroke(
            1.dp,
            if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(text = text)
    }
}

@Composable
fun KasirTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    prefix: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(KasirSpacing.CornerRadius),
    )
}

@Composable
fun KasirCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(KasirSpacing.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(KasirSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.sectionTitle,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
fun PriceText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.price,
    )
}

@Composable
fun PriceDisplay(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PriceText(text = value)
    }
}

enum class StatusBadgeTone {
    Neutral,
    Success,
    Warning,
    Error,
}

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusBadgeTone = StatusBadgeTone.Neutral,
) {
    val (containerColor, contentColor) = when (tone) {
        StatusBadgeTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        StatusBadgeTone.Success -> KasirSuccessContainer to KasirOnSuccessContainer
        StatusBadgeTone.Warning -> KasirWarningContainer to KasirOnWarningContainer
        StatusBadgeTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = KasirSpacing.ItemGap, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun KasirLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(KasirSpacing.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = KasirSpacing.ItemGap,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun KasirEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(KasirSpacing.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun KasirErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(KasirSpacing.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = KasirSpacing.ItemGap,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.supporting,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            KasirPrimaryButton(
                text = "Coba lagi",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun KasirTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KasirSpacing.ButtonMinHeight),
        enabled = enabled,
    ) {
        Text(text = text)
    }
}
