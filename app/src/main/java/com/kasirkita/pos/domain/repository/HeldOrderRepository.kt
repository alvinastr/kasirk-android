package com.kasirkita.pos.domain.repository

import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.HeldOrderCheckoutRequest
import com.kasirkita.pos.domain.model.HeldOrderCheckoutResult
import com.kasirkita.pos.domain.model.HeldOrderCreateRequest
import com.kasirkita.pos.domain.model.HeldOrderUpdateRequest
import com.kasirkita.pos.domain.model.HeldOrderPage

interface HeldOrderRepository {
    suspend fun create(request: HeldOrderCreateRequest): Result<HeldOrder>
    suspend fun list(outletId: String? = null, status: String = "OPEN", page: Int = 1, limit: Int = 20): Result<List<HeldOrder>>

    suspend fun listPage(
        outletId: String? = null,
        status: String = "OPEN",
        page: Int = 1,
        limit: Int = 20,
    ): Result<HeldOrderPage> = list(outletId, status, page, limit).map { items ->
        HeldOrderPage(items = items, total = items.size, page = page, limit = limit, totalPages = if (items.isEmpty()) 0 else 1)
    }
    suspend fun get(id: String): Result<HeldOrder>
    suspend fun update(request: HeldOrderUpdateRequest): Result<HeldOrder>
    suspend fun cancel(id: String, expectedVersion: Int): Result<HeldOrder>
    suspend fun checkout(request: HeldOrderCheckoutRequest): Result<HeldOrderCheckoutResult>
}
