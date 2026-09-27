package com.kasirkita.pos.presentation.authv2

import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

internal enum class AuthV2Action {
    RESOLVE_STORE,
    PIN_LOGIN,
}

internal fun authV2ErrorMessage(
    throwable: Throwable,
    action: AuthV2Action,
): String {
    if (throwable is IOException) {
        return "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    }

    if (throwable is HttpException) {
        val errorBody = runCatching {
            throwable.response()?.errorBody()?.string()
        }.getOrNull().orEmpty().uppercase(Locale.ROOT)

        return when {
            "STORE_NOT_FOUND" in errorBody ->
                "Kode toko tidak ditemukan. Periksa kode lalu coba lagi."
            "INVALID_CREDENTIALS" in errorBody ->
                "PIN salah atau pengguna tidak dapat masuk."
            "PIN_LOCKED" in errorBody ->
                "Terlalu banyak percobaan PIN. Tunggu beberapa saat lalu coba lagi."
            throwable.code() == 400 || throwable.code() == 422 ->
                "Data login belum valid. Periksa kembali data yang dimasukkan."
            else -> action.fallbackMessage()
        }
    }

    return action.fallbackMessage()
}

private fun AuthV2Action.fallbackMessage(): String = when (this) {
    AuthV2Action.RESOLVE_STORE -> "Toko tidak dapat diperiksa. Coba lagi."
    AuthV2Action.PIN_LOGIN -> "Login tidak berhasil. Coba lagi."
}
