package com.kasirkita.pos.data.model

import com.google.gson.annotations.SerializedName
import com.kasirkita.pos.domain.model.ResolvedStore
import com.kasirkita.pos.domain.model.StoreTenant
import com.kasirkita.pos.domain.model.StoreUser
import com.kasirkita.pos.domain.model.UserRole
import java.util.Locale

data class StoreResolveResponse(
    val tenant: StoreTenantResponse,
    val users: List<StoreUserResponse>,
)

data class StoreTenantResponse(
    val id: String,
    val name: String,
)

data class StoreUserResponse(
    val id: String,
    val name: String,
    val role: String,
    @SerializedName("outlet_id")
    val outletId: String?,
)

fun StoreResolveResponse.toDomain(): ResolvedStore = ResolvedStore(
    tenant = StoreTenant(
        id = tenant.id,
        name = tenant.name,
    ),
    users = users.map { user ->
        StoreUser(
            id = user.id,
            name = user.name,
            role = user.role.toAuthUserRole(),
            outletId = user.outletId,
        )
    },
)

internal fun String.toAuthUserRole(): UserRole = runCatching {
    UserRole.valueOf(uppercase(Locale.ROOT))
}.getOrElse {
    throw IllegalArgumentException("Unsupported user role: $this", it)
}
