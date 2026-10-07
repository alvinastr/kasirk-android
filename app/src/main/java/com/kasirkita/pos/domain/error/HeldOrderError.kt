package com.kasirkita.pos.domain.error

/**
 * Structured, domain-visible error for a failed Held Order call.
 *
 * Callers MUST branch on [errorCode] (a stable backend contract code) rather than
 * parsing [message], which is human-readable and may change at any time.
 *
 * This type previously lived as a nested class inside the data-layer
 * implementation, which forced presentation code to depend on a concrete
 * repository implementation in order to distinguish a version conflict from a
 * lifecycle conflict. Only the type moved across the architecture boundary; the
 * repository implementation remains solely responsible for reading the HTTP
 * error body, extracting `error_code`, extracting `message`, preserving the HTTP
 * status, and handling a malformed or empty body safely.
 */
class HeldOrderError(
    val httpCode: Int,
    val errorCode: String?,
    override val message: String,
) : Exception(message)
