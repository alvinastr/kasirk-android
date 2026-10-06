package com.kasirkita.pos.presentation.modifier

import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

internal fun modifierGroupCreateErrorMessage(throwable: Throwable): String {
    if (throwable is IOException) {
        return "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    }

    if (throwable is HttpException) {
        val errorBody = runCatching {
            throwable.response()?.errorBody()?.string()
        }.getOrNull().orEmpty().uppercase(Locale.ROOT)

        if (throwable.code() == 409 && "MODIFIER_GROUP_NAME_EXISTS" in errorBody) {
            return "Nama grup modifier sudah digunakan."
        }
    }

    return "Gagal membuat grup modifier"
}
