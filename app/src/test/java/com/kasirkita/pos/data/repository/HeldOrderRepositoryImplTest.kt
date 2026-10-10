package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.HeldOrderApi
import com.kasirkita.pos.data.model.CancelHeldOrderRequest
import com.kasirkita.pos.data.model.CheckoutHeldOrderRequest
import com.kasirkita.pos.data.model.CreateHeldOrderRequest
import com.kasirkita.pos.data.model.HeldOrderCheckoutResponse
import com.kasirkita.pos.data.model.HeldOrderItemRequest
import com.kasirkita.pos.data.model.HeldOrderResponse
import com.kasirkita.pos.data.model.HeldOrderSummaryResponse
import com.kasirkita.pos.data.model.HeldOrdersMetaResponse
import com.kasirkita.pos.data.model.HeldOrdersResponse
import com.kasirkita.pos.data.model.UpdateHeldOrderRequest
import com.kasirkita.pos.data.model.V1PaymentRequest
import com.kasirkita.pos.domain.error.HeldOrderError
import com.kasirkita.pos.domain.model.HeldOrderCheckoutRequest
import com.kasirkita.pos.domain.model.HeldOrderCheckoutResult
import com.kasirkita.pos.domain.model.HeldOrderCreateRequest
import com.kasirkita.pos.domain.model.HeldOrderUpdateRequest
import com.kasirkita.pos.data.model.PaymentResponse
import com.kasirkita.pos.data.model.TransactionDetailResponse
import com.kasirkita.pos.data.model.TransactionItemResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class HeldOrderRepositoryImplTest {

    @Test
    fun create_mapsRequestCorrectly() = runBlocking {
        val api = FakeHeldOrderApi(createResponse = Response.success(heldOrderResponse()))
        val repository = HeldOrderRepositoryImpl(api)

        val heldOrder = repository.create(createRequest()).getOrThrow()

        assertEquals(HELD_ORDER_ID, heldOrder.id)
        assertEquals(
            CreateHeldOrderRequest(
                outletId = OUTLET_ID,
                cashierSessionId = SESSION_ID,
                label = "Label",
                items = listOf(HeldOrderItemRequest(PRODUCT_ID, 2, listOf(MODIFIER_OPTION_ID), "Note")),
            ),
            api.lastCreateRequest,
        )
    }

    @Test
    fun list_mapsOpenHeldOrders() = runBlocking {
        val api = FakeHeldOrderApi(
            listResponse = HeldOrdersResponse(listOf(heldOrderSummaryResponse()), HeldOrdersMetaResponse(1, 20, 1, 1)),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val heldOrders = repository.list(outletId = OUTLET_ID).getOrThrow()

        assertEquals(listOf("OPEN"), heldOrders.map { it.status })
        assertEquals(OUTLET_ID, api.lastListOutletId)
        assertEquals("OPEN", api.lastListStatus)
        assertEquals(1, api.lastListPage)
        assertEquals(20, api.lastListLimit)
        assertTrue(heldOrders.single().items.isEmpty())
    }

    @Test
    fun listPage_preservesAuthoritativePaginationTotal() = runBlocking {
        val api = FakeHeldOrderApi(
            listResponse = HeldOrdersResponse(
                listOf(heldOrderSummaryResponse()),
                HeldOrdersMetaResponse(1, 20, 37, 2),
            ),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val page = repository.listPage(outletId = OUTLET_ID).getOrThrow()

        assertEquals(37, page.total)
        assertEquals(2, page.totalPages)
        assertEquals(1, page.items.size)
    }

    @Test
    fun get_mapsItemsAndModifiers() = runBlocking {
        val api = FakeHeldOrderApi(getResponse = heldOrderDetailResponse())
        val repository = HeldOrderRepositoryImpl(api)

        val heldOrder = repository.get(HELD_ORDER_ID).getOrThrow()

        assertEquals(HELD_ORDER_ID, api.lastGetId)
        assertEquals(PRODUCT_ID, heldOrder.items.single().productId)
        assertEquals(MODIFIER_OPTION_ID, heldOrder.items.single().modifiers.single().modifierOptionId)
    }

    @Test
    fun update_sendsExpectedVersion() = runBlocking {
        val api = FakeHeldOrderApi(updateResponse = Response.success(heldOrderResponse(version = 2)))
        val repository = HeldOrderRepositoryImpl(api)

        repository.update(updateRequest()).getOrThrow()

        assertEquals(HELD_ORDER_ID, api.lastUpdateId)
        assertEquals(
            UpdateHeldOrderRequest(
                expectedVersion = VERSION,
                label = "Updated",
                items = listOf(HeldOrderItemRequest(PRODUCT_ID, 3, emptyList(), null)),
            ),
            api.lastUpdateRequest,
        )
    }

    @Test
    fun cancel_sendsExpectedVersion() = runBlocking {
        val api = FakeHeldOrderApi(cancelResponse = Response.success(heldOrderResponse(status = "CANCELLED", version = 2)))
        val repository = HeldOrderRepositoryImpl(api)

        repository.cancel(HELD_ORDER_ID, expectedVersion = VERSION).getOrThrow()

        assertEquals(HELD_ORDER_ID, api.lastCancelId)
        assertEquals(CancelHeldOrderRequest(expectedVersion = VERSION), api.lastCancelRequest)
    }

    @Test
    fun checkout_sendsExpectedVersionClientTransactionIdAndPayment() = runBlocking {
        val api = FakeHeldOrderApi(checkoutResponse = Response.success(checkoutResponse()))
        val repository = HeldOrderRepositoryImpl(api)

        repository.checkout(checkoutRequest()).getOrThrow()

        assertEquals(HELD_ORDER_ID, api.lastCheckoutId)
        assertEquals(
            CheckoutHeldOrderRequest(
                expectedVersion = VERSION,
                clientTransactionId = CLIENT_TRANSACTION_ID,
                payment = V1PaymentRequest(method = "CASH", amountReceived = 60_000L),
            ),
            api.lastCheckoutRequest,
        )
    }

    @Test
    fun checkout_freshConversion_mapsReplayedFalseAndCanonicalTransaction() = runBlocking {
        val api = FakeHeldOrderApi(checkoutResponse = Response.success(checkoutResponse(replayed = false)))
        val repository = HeldOrderRepositoryImpl(api)

        val result = repository.checkout(checkoutRequest()).getOrThrow()

        assertEquals(false, result.replayed)
        assertEquals(TRANSACTION_ID, result.transaction.id)
        assertEquals(CLIENT_TRANSACTION_ID, result.transaction.clientTransactionId)
        assertEquals(PRODUCT_ID, result.transaction.items.single().productId)
        assertEquals("CASH", result.transaction.payments.single().method)
    }

    @Test
    fun checkout_replayedConversion_mapsReplayedTrueAndCanonicalTransaction() = runBlocking {
        val api = FakeHeldOrderApi(checkoutResponse = Response.success(checkoutResponse(replayed = true)))
        val repository = HeldOrderRepositoryImpl(api)

        val result = repository.checkout(checkoutRequest()).getOrThrow()

        assertEquals(true, result.replayed)
        assertEquals(TRANSACTION_ID, result.transaction.id)
        assertEquals(CLIENT_TRANSACTION_ID, result.transaction.clientTransactionId)
        assertEquals(PRODUCT_ID, result.transaction.items.single().productId)
        assertEquals("CASH", result.transaction.payments.single().method)
    }

    @Test
    fun checkout_networkFailure_returnsResultFailureWithoutSideEffects() = runBlocking {
        val expected = IOException("connection timeout")
        val api = FakeHeldOrderApi(checkoutFailure = expected)
        val repository = HeldOrderRepositoryImpl(api)

        val result = repository.checkout(checkoutRequest())

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
        assertFalse(api.offlineQueueTouched)
        assertFalse(api.cartTouched)
        assertFalse(api.printerTouched)
        assertFalse(api.drawerTouched)
    }

    @Test
    fun checkout_structuredError_preservesHeldOrderError() = runBlocking {
        val api = FakeHeldOrderApi(
            checkoutResponse = Response.error(
                409,
                "{\"error_code\":\"IDEMPOTENCY_PAYLOAD_MISMATCH\",\"message\":\"Payload mismatch\"}".toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val failure = repository.checkout(checkoutRequest()).exceptionOrNull()

        assertTrue(failure is HeldOrderError)
        failure as HeldOrderError
        assertEquals(409, failure.httpCode)
        assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH", failure.errorCode)
        assertEquals("Payload mismatch", failure.message)
    }

    @Test
    fun versionConflict_isPreserved() = runBlocking {
        val api = FakeHeldOrderApi(
            updateResponse = Response.error(
                409,
                "{\"error_code\":\"HELD_ORDER_VERSION_CONFLICT\",\"message\":\"Held order version is stale\"}".toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val failure = repository.update(updateRequest()).exceptionOrNull()

        assertTrue(failure is HeldOrderError)
        assertEquals(409, (failure as HeldOrderError).httpCode)
        assertEquals("HELD_ORDER_VERSION_CONFLICT", failure.errorCode)
        assertEquals("Held order version is stale", failure.message)
    }

    @Test
    fun lifecycleConflict_isPreserved() = runBlocking {
        val api = FakeHeldOrderApi(
            cancelResponse = Response.error(
                409,
                "{\"error_code\":\"HELD_ORDER_NOT_OPEN\",\"message\":\"Held order is not open\"}".toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val failure = repository.cancel(HELD_ORDER_ID, VERSION).exceptionOrNull()

        assertTrue(failure is HeldOrderError)
        assertEquals("HELD_ORDER_NOT_OPEN", (failure as HeldOrderError).errorCode)
        assertEquals("Held order is not open", failure.message)
    }

    @Test
    fun malformedBody_isHandledSafelyAndPreservesStatus() = runBlocking {
        val api = FakeHeldOrderApi(
            updateResponse = Response.error(
                500,
                "not-json-at-all".toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val failure = repository.update(updateRequest()).exceptionOrNull()

        assertTrue(failure is HeldOrderError)
        failure as HeldOrderError
        assertEquals(500, failure.httpCode)
        assertNull(failure.errorCode)
        // Body could not be parsed, so the Retrofit HTTP message is used verbatim
        // rather than an empty string or an invented value.
        assertEquals("Response.error()", failure.message)
    }

    @Test
    fun emptyJsonBody_yieldsNullErrorCodeAndNullMessage() = runBlocking {
        val api = FakeHeldOrderApi(
            cancelResponse = Response.error(
                404,
                "{}".toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val failure = repository.cancel(HELD_ORDER_ID, VERSION).exceptionOrNull()

        assertTrue(failure is HeldOrderError)
        failure as HeldOrderError
        assertEquals(404, failure.httpCode)
        assertNull(failure.errorCode)
        assertEquals("Response.error()", failure.message)
    }

    @Test
    fun arrayMessage_isJoinedSafely() = runBlocking {
        val api = FakeHeldOrderApi(
            updateResponse = Response.error(
                422,
                "{\"error_code\":\"HELD_ORDER_NOT_OPEN\",\"message\":[\"first\",\"second\"]}"
                    .toResponseBody("application/json".toMediaType()),
            ),
        )
        val repository = HeldOrderRepositoryImpl(api)

        val failure = repository.update(updateRequest()).exceptionOrNull()

        assertTrue(failure is HeldOrderError)
        failure as HeldOrderError
        assertEquals(422, failure.httpCode)
        assertEquals("HELD_ORDER_NOT_OPEN", failure.errorCode)
        assertEquals("first, second", failure.message)
    }

    @Test
    fun networkFailure_doesNotEnqueueOfflineTransaction() = runBlocking {
        val expected = IOException("network unavailable")
        val api = FakeHeldOrderApi(createFailure = expected)
        val repository = HeldOrderRepositoryImpl(api)

        val result = repository.create(createRequest())

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
        assertFalse(api.offlineQueueTouched)
    }

    @Test
    fun repositoryDoesNotMutateCartPrintOrDrawer() = runBlocking {
        val api = FakeHeldOrderApi(createResponse = Response.success(heldOrderResponse()))
        val repository = HeldOrderRepositoryImpl(api)

        repository.create(createRequest()).getOrThrow()

        assertFalse(api.cartTouched)
        assertFalse(api.printerTouched)
        assertFalse(api.drawerTouched)
    }

    private class FakeHeldOrderApi(
        private val createResponse: Response<HeldOrderResponse> = Response.success(heldOrderResponse()),
        private val listResponse: HeldOrdersResponse = HeldOrdersResponse(emptyList(), HeldOrdersMetaResponse(1, 20, 0, 0)),
        private val getResponse: HeldOrderResponse = heldOrderResponse(),
        private val updateResponse: Response<HeldOrderResponse> = Response.success(heldOrderResponse()),
        private val cancelResponse: Response<HeldOrderResponse> = Response.success(heldOrderResponse()),
        private val checkoutResponse: Response<HeldOrderCheckoutResponse> = Response.success(checkoutResponse()),
        private val createFailure: Throwable? = null,
        private val checkoutFailure: Throwable? = null,
    ) : HeldOrderApi {
        var lastCreateRequest: CreateHeldOrderRequest? = null
        var lastListOutletId: String? = null
        var lastListStatus: String? = null
        var lastListPage: Int? = null
        var lastListLimit: Int? = null
        var lastGetId: String? = null
        var lastUpdateId: String? = null
        var lastUpdateRequest: UpdateHeldOrderRequest? = null
        var lastCancelId: String? = null
        var lastCancelRequest: CancelHeldOrderRequest? = null
        var lastCheckoutId: String? = null
        var lastCheckoutRequest: CheckoutHeldOrderRequest? = null
        var offlineQueueTouched = false
        var cartTouched = false
        var printerTouched = false
        var drawerTouched = false

        override suspend fun createHeldOrder(request: CreateHeldOrderRequest): Response<HeldOrderResponse> {
            lastCreateRequest = request
            createFailure?.let { throw it }
            return createResponse
        }

        override suspend fun getHeldOrders(
            outletId: String?,
            status: String?,
            page: Int,
            limit: Int,
        ): Response<HeldOrdersResponse> {
            lastListOutletId = outletId
            lastListStatus = status
            lastListPage = page
            lastListLimit = limit
            return Response.success(listResponse)
        }

        override suspend fun getHeldOrder(id: String): Response<HeldOrderResponse> {
            lastGetId = id
            return Response.success(getResponse)
        }

        override suspend fun updateHeldOrder(id: String, request: UpdateHeldOrderRequest): Response<HeldOrderResponse> {
            lastUpdateId = id
            lastUpdateRequest = request
            return updateResponse
        }

        override suspend fun cancelHeldOrder(id: String, request: CancelHeldOrderRequest): Response<HeldOrderResponse> {
            lastCancelId = id
            lastCancelRequest = request
            return cancelResponse
        }

        override suspend fun checkoutHeldOrder(id: String, request: CheckoutHeldOrderRequest): Response<HeldOrderCheckoutResponse> {
            lastCheckoutId = id
            lastCheckoutRequest = request
            checkoutFailure?.let { throw it }
            return checkoutResponse
        }
    }

    private companion object {
        const val HELD_ORDER_ID = "held-order-id"
        const val OUTLET_ID = "outlet-id"
        const val SESSION_ID = "session-id"
        const val USER_ID = "user-id"
        const val VERSION = 1
        const val PRODUCT_ID = "product-id"
        const val MODIFIER_OPTION_ID = "modifier-option-id"
        const val MODIFIER_GROUP_ID = "modifier-group-id"
        const val HELD_ORDER_ITEM_ID = "held-order-item-id"
        const val HELD_ORDER_MODIFIER_ID = "held-order-modifier-id"
        const val CLIENT_TRANSACTION_ID = "client-transaction-id"
        const val TRANSACTION_ID = "transaction-id"

        fun createRequest() = HeldOrderCreateRequest(
            outletId = OUTLET_ID,
            cashierSessionId = SESSION_ID,
            label = "Label",
            items = listOf(
                com.kasirkita.pos.domain.model.HeldOrderItemRequest(
                    productId = PRODUCT_ID,
                    quantity = 2,
                    modifierOptionIds = listOf(MODIFIER_OPTION_ID),
                    note = "Note",
                ),
            ),
        )

        fun updateRequest() = HeldOrderUpdateRequest(
            id = HELD_ORDER_ID,
            expectedVersion = VERSION,
            label = "Updated",
            items = listOf(
                com.kasirkita.pos.domain.model.HeldOrderItemRequest(
                    productId = PRODUCT_ID,
                    quantity = 3,
                    modifierOptionIds = emptyList(),
                    note = null,
                ),
            ),
        )

        fun checkoutRequest() = HeldOrderCheckoutRequest(
            id = HELD_ORDER_ID,
            expectedVersion = VERSION,
            clientTransactionId = CLIENT_TRANSACTION_ID,
            payment = com.kasirkita.pos.domain.model.V1Payment(method = "CASH", amountReceived = 60_000L),
        )

        fun heldOrderResponse(status: String = "OPEN", version: Int = VERSION) = HeldOrderResponse(
            heldOrderId = HELD_ORDER_ID,
            outletId = OUTLET_ID,
            cashierSessionId = SESSION_ID,
            cashierUserId = USER_ID,
            cashierName = "Cashier",
            label = "Label",
            status = status,
            version = version,
            subtotalEstimate = 50_000L,
            taxEstimate = 0L,
            totalEstimate = 50_000L,
            itemCount = 2,
            createdAt = "2026-10-07T10:00:00.000Z",
            updatedAt = "2026-10-07T10:00:00.000Z",
            cancelledAt = null,
            convertedAt = null,
        )

        fun heldOrderSummaryResponse(status: String = "OPEN", version: Int = VERSION) = HeldOrderSummaryResponse(
            heldOrderId = HELD_ORDER_ID,
            outletId = OUTLET_ID,
            cashierSessionId = SESSION_ID,
            cashierUserId = USER_ID,
            cashierName = "Cashier",
            label = "Label",
            status = status,
            version = version,
            subtotalEstimate = 50_000L,
            taxEstimate = 0L,
            totalEstimate = 50_000L,
            itemCount = 2,
            createdAt = "2026-10-07T10:00:00.000Z",
            updatedAt = "2026-10-07T10:00:00.000Z",
            cancelledAt = null,
            convertedAt = null,
        )

        fun heldOrderDetailResponse(): HeldOrderResponse = heldOrderResponse().copy(
            convertedTransactionId = null,
            items = listOf(
                com.kasirkita.pos.data.model.HeldOrderItemResponse(
                    heldOrderItemId = HELD_ORDER_ITEM_ID,
                    productId = PRODUCT_ID,
                    quantity = 2,
                    productName = "Product",
                    sku = "SKU",
                    basePrice = 12_500L,
                    effectivePrice = 15_500L,
                    lineSubtotal = 31_000L,
                    lineDiscount = 0L,
                    lineTotal = 31_000L,
                    note = "Note",
                    displayOrder = 0,
                    modifiers = listOf(
                        com.kasirkita.pos.data.model.HeldOrderItemModifierResponse(
                            heldOrderItemModifierId = HELD_ORDER_MODIFIER_ID,
                            modifierGroupId = MODIFIER_GROUP_ID,
                            modifierOptionId = MODIFIER_OPTION_ID,
                            groupName = "Group",
                            optionName = "Option",
                            priceDelta = 3_000L,
                        ),
                    ),
                ),
            ),
        )

        fun transactionResponse() = TransactionDetailResponse(
            transactionId = TRANSACTION_ID,
            clientTransactionId = CLIENT_TRANSACTION_ID,
            outletId = OUTLET_ID,
            userId = USER_ID,
            customerId = null,
            cashierSessionId = SESSION_ID,
            shiftId = null,
            status = "COMPLETED",
            subtotal = 50_000L,
            discount = 0L,
            tax = 0L,
            total = 50_000L,
            items = listOf(TransactionItemResponse("item-id", PRODUCT_ID, 2, 25_000L, 50_000L)),
            payments = listOf(PaymentResponse("payment-id", "CASH", "PAID", 50_000L, 60_000L, 10_000L, "2026-10-07T10:00:00.000Z")),
            change = 10_000L,
            createdAt = "2026-10-07T10:00:00.000Z",
        )

        fun checkoutResponse(replayed: Boolean = false) = HeldOrderCheckoutResponse(
            transaction = transactionResponse(),
            replayed = replayed,
        )
    }
}
