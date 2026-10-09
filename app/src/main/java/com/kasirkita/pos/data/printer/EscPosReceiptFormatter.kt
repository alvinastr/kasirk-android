package com.kasirkita.pos.data.printer

import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptDocument
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.model.ReceiptTextAlignment
import com.kasirkita.pos.domain.usecase.BuildReceiptDocumentUseCase
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
    private val documentBuilder = BuildReceiptDocumentUseCase()

    /**
     * Legacy entry point: build the canonical document without settings.
     */
    fun formatReceipt(receipt: Receipt, paperWidthMm: Int): ByteArray =
        formatReceipt(documentBuilder(receipt, settings = null, paperWidthMm = paperWidthMm))

    /**
     * Settings-aware entry point for M17E, when repository settings reach printing.
     */
    fun formatReceipt(receipt: Receipt, settings: ReceiptSettings?, paperWidthMm: Int): ByteArray =
        formatReceipt(documentBuilder(receipt, settings, paperWidthMm))

    fun formatReceipt(document: ReceiptDocument): ByteArray = buildString {
        append(ESC_INIT)
        document.lines.forEach { line ->
            requireNoUnsafeTextControls(line.text)
            append(alignmentCommand(line.alignment))
            if (line.style == com.kasirkita.pos.domain.model.ReceiptLineStyle.Emphasis) append(ESC_BOLD_ON)
            append(line.text)
            if (line.style == com.kasirkita.pos.domain.model.ReceiptLineStyle.Emphasis) append(ESC_BOLD_OFF)
            append("\n")
        }
        append(ESC_FEED_CUT)
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

    private fun requireNoUnsafeTextControls(text: String) {
        val firstUnsafe = text.firstOrNull { it == '\u007F' || it.code < 0x20 || it.code in 0x80..0x9F }
        require(firstUnsafe == null) {
            "ReceiptDocument contains unsafe control character U+${firstUnsafe!!.code.toString(16).uppercase().padStart(4, '0')}"
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
        const val ESC_FEED_CUT = "\n\n\n\u001Bd\u0005\u001Bm" // Feed 5 lines + partial cut
    }
}
