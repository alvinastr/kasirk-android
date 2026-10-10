package com.kasirkita.pos.data.repository

import com.google.gson.JsonParser
import com.kasirkita.pos.data.api.HeldOrderApi
import com.kasirkita.pos.data.model.CancelHeldOrderRequest
import com.kasirkita.pos.data.model.CheckoutHeldOrderRequest
import com.kasirkita.pos.data.model.CreateHeldOrderRequest
import com.kasirkita.pos.data.model.HeldOrderItemRequest
import com.kasirkita.pos.data.model.UpdateHeldOrderRequest
import com.kasirkita.pos.data.model.V1PaymentRequest
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.error.HeldOrderError
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.domain.model.HeldOrderCheckoutRequest
import com.kasirkita.pos.domain.model.HeldOrderCheckoutResult
import com.kasirkita.pos.domain.model.HeldOrderCreateRequest
import com.kasirkita.pos.domain.model.HeldOrderUpdateRequest
import com.kasirkita.pos.domain.model.HeldOrderPage
import com.kasirkita.pos.domain.repository.HeldOrderRepository
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeldOrderRepositoryImpl @Inject constructor(
    private val api: HeldOrderApi,
) : HeldOrderRepository {

    override suspend fun create(request: HeldOrderCreateRequest): Result<HeldOrder> = runCatching {
        api.createHeldOrder(
            CreateHeldOrderRequest(
                outletId = request.outletId,
                cashierSessionId = request.cashierSessionId,
                label = request.label,
                items = request.items.map { it.toData() },
            ),
        ).bodyOrThrow().toDomain()
    }

    override suspend fun list(
        outletId: String?,
        status: String,
        page: Int,
        limit: Int,
    ): Result<List<HeldOrder>> = runCatching {
        listPage(outletId, status, page, limit).getOrThrow().items
    }

    override suspend fun listPage(
        outletId: String?,
        status: String,
        page: Int,
        limit: Int,
    ): Result<HeldOrderPage> = runCatching {
        api.getHeldOrders(outletId, status, page, limit).bodyOrThrow().let { response ->
            HeldOrderPage(
                items = response.data.map { it.toDomain() },
                total = response.meta.total,
                page = response.meta.page,
                limit = response.meta.limit,
                totalPages = response.meta.totalPages,
            )
        }
    }

    override suspend fun get(id: String): Result<HeldOrder> = runCatching {
        api.getHeldOrder(id).bodyOrThrow().toDomain()
    }

    override suspend fun update(request: HeldOrderUpdateRequest): Result<HeldOrder> = runCatching {
        api.updateHeldOrder(
            request.id,
            UpdateHeldOrderRequest(
                expectedVersion = request.expectedVersion,
                label = request.label,
                items = request.items?.map { it.toData() },
            ),
        ).bodyOrThrow().toDomain()
    }

    override suspend fun cancel(id: String, expectedVersion: Int): Result<HeldOrder> = runCatching {
        api.cancelHeldOrder(id, CancelHeldOrderRequest(expectedVersion)).bodyOrThrow().toDomain()
    }

    override suspend fun checkout(request: HeldOrderCheckoutRequest): Result<HeldOrderCheckoutResult> = runCatching {
        val response = api.checkoutHeldOrder(
            request.id,
            CheckoutHeldOrderRequest(
                expectedVersion = request.expectedVersion,
                clientTransactionId = request.clientTransactionId,
                payment = V1PaymentRequest(
                    method = request.payment.method,
                    amountReceived = request.payment.amountReceived,
                ),
            ),
        ).bodyOrThrow()
        HeldOrderCheckoutResult(
            transaction = response.transaction.toDomain(),
            replayed = response.replayed,
        )
    }

    private fun com.kasirkita.pos.domain.model.HeldOrderItemRequest.toData() = HeldOrderItemRequest(
        productId = productId,
        quantity = quantity,
        modifierOptionIds = modifierOptionIds,
        note = note,
    )

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (isSuccessful) return body() ?: throw HeldOrderError(code(), null, "Empty response body")
        val raw = errorBody()?.string()
        var errorCode: String? = null
        var message: String? = null
        if (!raw.isNullOrBlank()) {
            runCatching {
                val json = JsonParser.parseString(raw).asJsonObject
                errorCode = json.get("error_code")?.takeUnless { it.isJsonNull }?.asString
                message = json.get("message")?.let { value ->
                    if (value.isJsonArray) value.asJsonArray.joinToString(", ") { it.asString }
                    else if (!value.isJsonNull) value.asString else null
                }
            }
            // If parsing threw, keep errorCode if we got it, message falls back to Retrofit message
        }
        throw HeldOrderError(code(), errorCode, message ?: message())
    }
}
