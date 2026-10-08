package com.kasirkita.pos.domain.model

/** Server-authoritative receipt template settings for one tenant/outlet. */
data class ReceiptSettings(
    val tenantId: String,
    val outletId: String,
    val header: ReceiptHeaderSettings,
    val visibility: ReceiptVisibilitySettings,
    val footer: ReceiptFooterSettings,
    val templateVersion: Int,
    val createdAt: String?,
    val updatedAt: String?,
)

data class ReceiptHeaderSettings(
    val storeName: String,
    val outletName: String,
    val address: String?,
    val phone: String?,
    val additionalText: String?,
)

data class ReceiptVisibilitySettings(
    val showSku: Boolean,
    val showModifiers: Boolean,
    val showItemNotes: Boolean,
    val showCashier: Boolean,
    val showCustomer: Boolean,
)

data class ReceiptFooterSettings(
    val thankYouText: String,
    val promoText: String?,
)