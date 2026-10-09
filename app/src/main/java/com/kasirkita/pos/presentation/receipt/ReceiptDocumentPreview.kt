package com.kasirkita.pos.presentation.receipt

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptLine
import com.kasirkita.pos.domain.model.ReceiptLineStyle
import com.kasirkita.pos.domain.model.ReceiptTextAlignment

@Composable
fun ReceiptDocumentPreview(
    document: ReceiptDocument,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .widthIn(min = if (document.characterWidth == 48) 384.dp else 256.dp),
    ) {
        document.lines.forEach { line ->
            ReceiptPreviewLine(line = line)
        }
    }
}

@Composable
private fun ReceiptPreviewLine(line: ReceiptLine) {
    Text(
        text = line.text.ifEmpty { " " },
        modifier = Modifier.fillMaxWidth(),
        textAlign = when (line.alignment) {
            ReceiptTextAlignment.Left -> TextAlign.Start
            ReceiptTextAlignment.Center -> TextAlign.Center
            ReceiptTextAlignment.Right -> TextAlign.End
        },
        fontFamily = FontFamily.Monospace,
        fontWeight = when (line.style) {
            ReceiptLineStyle.Emphasis -> FontWeight.Bold
            ReceiptLineStyle.Normal,
            ReceiptLineStyle.Separator,
            -> FontWeight.Normal
        },
        color = if (line.style == ReceiptLineStyle.Separator) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}
