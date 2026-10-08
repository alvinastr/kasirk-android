package com.kasirkita.pos.domain.error

/**
 * Structured, domain-visible error for a failed receipt-settings call.
 *
 * Callers MUST branch on [errorCode] (a stable backend contract code) rather than
 * parsing [message], which is human-readable and may change at any time.
 */
class ReceiptSettingsError(
    val httpCode: Int,
    val errorCode: String?,
    override val message: String,
) : Exception(message)