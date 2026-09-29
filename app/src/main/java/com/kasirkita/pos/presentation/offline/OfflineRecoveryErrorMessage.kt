package com.kasirkita.pos.presentation.offline

import java.io.IOException
import java.util.Locale

internal fun offlineRecoveryErrorMessage(throwable: Throwable): String = when (throwable) {
    is IOException -> "Koneksi tidak tersedia. Transaksi akan dicoba lagi secara otomatis."
    else -> offlineRecoveryErrorMessage(throwable.message)
}

internal fun offlineRecoveryErrorMessage(rawMessage: String?): String {
    val message = rawMessage.orEmpty().uppercase(Locale.ROOT)
    return when {
        "INSUFFICIENT_STOCK" in message || "INSUFFICIENT STOCK" in message ->
            "Stok tidak mencukupi saat transaksi disinkronkan."
        "OPEN_SHIFT_REQUIRED" in message ->
            "Buka shift yang sesuai sebelum mencoba transaksi ini lagi."
        "SHIFT_OUTLET_MISMATCH" in message ->
            "Outlet transaksi tidak sesuai dengan shift yang sedang aktif."
        "INVALID LOCAL TRANSACTION PAYLOAD" in message ->
            "Data transaksi offline tidak lengkap dan perlu diperiksa."
        "FINANCIAL SNAPSHOT" in message ->
            "Nilai transaksi di server berbeda dari nilai saat checkout."
        "UNABLE TO REACH" in message || "TRANSPORT" in message ->
            "Koneksi tidak tersedia. Transaksi akan dicoba lagi secara otomatis."
        "TEMPORARILY UNAVAILABLE" in message || "EMPTY RESPONSE" in message ||
            "INCOMPLETE RESPONSE" in message ->
            "Server belum dapat memproses sinkronisasi. Sistem akan mencoba lagi."
        "AUTHENTICATION" in message || "SESSION" in message || "SESI" in message ->
            "Sesi login tidak tersedia. Silakan masuk kembali."
        else -> "Transaksi offline tidak dapat diproses. Periksa kembali lalu coba lagi."
    }
}
