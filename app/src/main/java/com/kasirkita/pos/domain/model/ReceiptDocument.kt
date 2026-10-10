package com.kasirkita.pos.domain.model

/**
 * Preview-facing wrapper around the canonical document.
 *
 * Holds only pure render facts so a Compose preview (M17D) can draw the exact
 * same lines the ESC/POS renderer prints, without any Compose or Android
 * dependency in this layer.
 */
data class ReceiptRenderModel(val document: ReceiptDocument) {
    val paperWidthMm: Int get() = document.paperWidthMm
    val characterWidth: Int get() = document.characterWidth
    val lines: List<ReceiptLine> get() = document.lines
    val textLines: List<String> get() = document.lines.map { it.text }

    fun block(type: ReceiptBlockType): ReceiptBlock? = document.blocks.firstOrNull { it.type == type }
}

data class ReceiptDocument(
    val paperWidthMm: Int,
    val characterWidth: Int,
    val blocks: List<ReceiptBlock>,
    val headerAdditionalTextPresent: Boolean = false,
    val footerPromoTextPresent: Boolean = false,
) {
    val lines: List<ReceiptLine> = blocks.flatMap { it.lines }
}

data class ReceiptBlock(
    val type: ReceiptBlockType,
    val lines: List<ReceiptLine>,
)

enum class ReceiptBlockType {
    Header,
    TransactionMetadata,
    Items,
    Totals,
    Payment,
    Footer,
}

data class ReceiptLine(
    val text: String,
    val alignment: ReceiptTextAlignment = ReceiptTextAlignment.Left,
    val style: ReceiptLineStyle = ReceiptLineStyle.Normal,
)

enum class ReceiptTextAlignment {
    Left,
    Center,
    Right,
}

enum class ReceiptLineStyle {
    Normal,
    Emphasis,
    Separator,
}
