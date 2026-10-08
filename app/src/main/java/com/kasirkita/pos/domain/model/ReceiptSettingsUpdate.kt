package com.kasirkita.pos.domain.model

/**
 * The 12 editable receipt-template fields.
 *
 * Server-owned fields (tenantId, outletId, templateVersion, createdAt, updatedAt)
 * are deliberately absent: the backend derives tenant from the JWT, owns the
 * outlet path parameter, sets template_version, and stamps timestamps.
 */
data class ReceiptSettingsUpdate(
    val headerStoreName: String,
    val headerOutletName: String,
    val headerAddress: String?,
    val headerPhone: String?,
    val headerAdditionalText: String?,
    val showSku: Boolean,
    val showModifiers: Boolean,
    val showItemNotes: Boolean,
    val showCashier: Boolean,
    val showCustomer: Boolean,
    val footerThankYouText: String,
    val footerPromoText: String?,
)