package com.kasirkita.pos.domain.model

/** Immutable display and transport snapshot of one selected modifier option. */
data class CartModifierSelectionSnapshot(
    val optionId: String,
    val groupId: String? = null,
    val groupName: String? = null,
    val optionName: String,
    val priceDelta: Long = 0L,
)

data class CartLineKey private constructor(
    val value: String,
    val productId: String,
    val modifierOptionIds: List<String>,
    val note: String?,
) {
    companion object {
        fun from(productId: String, modifierOptionIds: List<String>, note: String?): CartLineKey {
            require(productId.isNotBlank()) { "productId is required" }
            val normalizedIds = modifierOptionIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            val normalizedNote = normalizeItemNote(note)
            val canonical = buildString {
                append("p:").append(lengthPrefixed(productId))
                append("|m:").append(normalizedIds.size).append(":")
                normalizedIds.forEach { append(lengthPrefixed(it)).append(";") }
                append("|n:").append(normalizedNote?.let(::lengthPrefixed) ?: "null")
            }
            return CartLineKey(
                if (normalizedIds.isEmpty() && normalizedNote == null) productId else canonical,
                productId, normalizedIds, normalizedNote,
            )
        }
    }
}

fun normalizeItemNote(note: String?): String? {
    val normalized = note?.trim()?.takeUnless { it.isEmpty() }
    require(normalized == null || normalized.length <= 255) { "Item note must be at most 255 characters" }
    return normalized
}

private fun lengthPrefixed(value: String): String = "${value.length}:$value"
