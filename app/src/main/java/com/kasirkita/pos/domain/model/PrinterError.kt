package com.kasirkita.pos.domain.model

/**
 * Printer operation error types.
 * User-facing Indonesian messages defined separately.
 */
enum class PrinterError {
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    NO_PRINTER_CONFIGURED,
    DEVICE_NOT_FOUND,
    CONNECTION_FAILED,
    WRITE_FAILED,
    RECEIPT_FETCH_FAILED,
    UNKNOWN,
}
