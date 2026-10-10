package com.kasirkita.pos.data.printer

import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptTextAlignment
import com.kasirkita.pos.BuildConfig
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ESC/POS byte renderer.
 *
 * Content decisions live in [BuildReceiptDocumentUseCase]; this class only
 * translates canonical receipt lines and styles into ESC/POS commands.
 * Physical compatibility with VSC TM-58D unverified until M16.
 */
@Singleton
class EscPosReceiptFormatter @Inject constructor() {
    fun formatReceipt(document: ReceiptDocument): ByteArray = buildString {
        if (BuildConfig.DEBUG) {
            runCatching {
                Log.d(
                    "ReceiptPrint",
                    "document_lines=${document.lines.size} header_additional=${document.headerAdditionalTextPresent} footer_promo=${document.footerPromoTextPresent}",
                )
            }
        }
        append(ESC_INIT)
        document.lines.forEach { line ->
            append(alignmentCommand(line.alignment))
            if (line.style == com.kasirkita.pos.domain.model.ReceiptLineStyle.Emphasis) append(ESC_BOLD_ON)
            append(line.text.toPrinterSafeText())
            if (line.style == com.kasirkita.pos.domain.model.ReceiptLineStyle.Emphasis) append(ESC_BOLD_OFF)
            append("\n")
        }
        // Reset state before the terminal manual-tear feed; these are the final bytes.
        append(ESC_BOLD_OFF)
        append(ESC_ALIGN_LEFT)
        append(ESC_FEED)
    }.toByteArray(Charsets.UTF_8)

    /**
     * Generate drawer pulse command.
     * ESC p m t1 t2
     * Generic pin 2 pulse: ESC p 0 50 250
     */
    fun formatDrawerPulse(profile: DrawerPulseProfile): ByteArray {
        return when (profile) {
            DrawerPulseProfile.DEFAULT -> byteArrayOf(
                0x1B, 0x70, // ESC p
                0x00, // Pin 2
                0x32, // t1 = 50ms on-time
                0xFA.toByte(), // t2 = 250ms off-time
            )
        }
    }

    private fun alignmentCommand(alignment: ReceiptTextAlignment): String = when (alignment) {
        ReceiptTextAlignment.Left -> ESC_ALIGN_LEFT
        ReceiptTextAlignment.Center -> ESC_ALIGN_CENTER
        ReceiptTextAlignment.Right -> ESC_ALIGN_RIGHT
    }

    private fun String.toPrinterSafeText(): String = buildString(length) {
        this@toPrinterSafeText.forEach { character ->
            if (character != '\u007F' && character.code >= 0x20 && character.code !in 0x80..0x9F) {
                append(character)
            }
        }
    }

    private companion object {
        // ESC/POS commands
        const val ESC_INIT = "\u001B@" // Initialize printer
        const val ESC_ALIGN_LEFT = "\u001Ba\u0000"
        const val ESC_ALIGN_CENTER = "\u001Ba\u0001"
        const val ESC_ALIGN_RIGHT = "\u001Ba\u0002"
        const val ESC_BOLD_ON = "\u001BE\u0001"
        const val ESC_BOLD_OFF = "\u001BE\u0000"
        const val ESC_FEED = "\n\n" // Two manual-tear lines; cutter support is not modeled.
    }
}
