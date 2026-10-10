package com.kasirkita.pos.presentation.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptLine
import com.kasirkita.pos.domain.model.ReceiptLineStyle
import com.kasirkita.pos.domain.model.ReceiptTextAlignment

@Composable
fun ReceiptDocumentPreview(
    document: ReceiptDocument,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val paperWidth = ReceiptPreviewWidthPolicy.widthDp(
            paperWidthMm = document.paperWidthMm,
            availableWidthDp = maxWidth.value,
        ).dp
        val fontScale = LocalDensity.current.fontScale
        val usableWidthDp = (paperWidth.value - PAPER_HORIZONTAL_PADDING_DP * 2).coerceAtLeast(1f)
        val fontSizeSp = minOf(
            MAX_FONT_SIZE_SP,
            usableWidthDp / document.characterWidth / MONOSPACE_CHARACTER_EM / fontScale,
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(paperWidth)
                .border(1.dp, PaperBorder)
                .background(PaperColor)
                .padding(
                    horizontal = PAPER_HORIZONTAL_PADDING_DP.dp,
                    vertical = PAPER_VERTICAL_PADDING_DP.dp,
                ),
        ) {
            document.lines.forEach { line ->
                ReceiptPreviewLine(
                    line = line,
                    fontSizeSp = fontSizeSp,
                )
            }
        }
    }
}

@Composable
private fun ReceiptPreviewLine(
    line: ReceiptLine,
    fontSizeSp: Float,
) {
    Text(
        text = line.text.ifEmpty { " " },
        modifier = Modifier.fillMaxWidth(),
        textAlign = when (line.alignment) {
            ReceiptTextAlignment.Left -> TextAlign.Start
            ReceiptTextAlignment.Center -> TextAlign.Center
            ReceiptTextAlignment.Right -> TextAlign.End
        },
        fontFamily = FontFamily.Monospace,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * LINE_HEIGHT_MULTIPLIER).sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        fontWeight = when (line.style) {
            ReceiptLineStyle.Emphasis -> FontWeight.Bold
            ReceiptLineStyle.Normal,
            ReceiptLineStyle.Separator,
            -> FontWeight.Normal
        },
        color = if (line.style == ReceiptLineStyle.Separator) {
            SeparatorColor
        } else {
            InkColor
        },
    )
}

private val PaperColor = Color(0xFFFFFCF2)
private val PaperBorder = Color(0xFFD8D1C2)
private val InkColor = Color(0xFF181713)
private val SeparatorColor = Color(0xFF57534A)
private const val PAPER_HORIZONTAL_PADDING_DP = 14f
private const val PAPER_VERTICAL_PADDING_DP = 18f
private const val MAX_FONT_SIZE_SP = 14f
private const val MONOSPACE_CHARACTER_EM = 0.62f
private const val LINE_HEIGHT_MULTIPLIER = 1.25f
