package com.kasirkita.pos.data.repository

import com.kasirkita.pos.data.api.TransactionApi
import com.kasirkita.pos.data.model.CreateTransactionRequest
import com.kasirkita.pos.data.model.PaymentResponse
import com.kasirkita.pos.data.model.TransactionDetailResponse
import com.kasirkita.pos.data.model.TransactionItemResponse
import com.kasirkita.pos.data.model.TransactionResponse
import com.kasirkita.pos.data.model.TransactionsMetaResponse
import com.kasirkita.pos.data.model.TransactionsResponse
import com.kasirkita.pos.data.model.V1CreateTransactionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class TransactionHistoryRepositoryTest {

    @Test
    fun getTransactions_mapsSummaryAndRequestsMaximumPage() = runBlocking {
        val api = FakeTransactionApi(
            transactionsResponse = TransactionsResponse(
                data = listOf(summaryResponse()),
                meta = TransactionsMetaResponse(1, 100, 1, 1),
            ),
        )
        val repository = TransactionRepositoryImpl(api)

        val transactions = repository.getTransactions().getOrThrow()

        assertEquals(1, transactions.size)
        assertEquals(TRANSACTION_ID, transactions.single().id)
        assertEquals(25_000L, transactions.single().total)
        assertEquals(1, api.lastPage)
        assertEquals(100, api.lastLimit)
    }

    @Test
    fun getTransactionDetail_mapsItemsAndPayment() = runBlocking {
        val repository = TransactionRepositoryImpl(
            FakeTransactionApi(detailResponse = detailResponse()),
        )

        val transaction = repository.getTransactionDetail(TRANSACTION_ID).getOrThrow()

        assertEquals("product-id", transaction.items.single().productId)
        assertEquals("CASH", transaction.payments.single().method)
        assertEquals(5_000L, transaction.change)
    }

    @Test
    fun getTransactions_whenApiFails_returnsSameFailure() = runBlocking {
        val expected = IOException("network unavailable")
        val repository = TransactionRepositoryImpl(
            FakeTransactionApi(transactionsFailure = expected),
        )

        val result = repository.getTransactions()

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    private class FakeTransactionApi(
        private val transactionsResponse: TransactionsResponse = TransactionsResponse(
            data = emptyList(),
            meta = TransactionsMetaResponse(1, 100, 0, 0),
        ),
        private val detailResponse: TransactionDetailResponse = detailResponse(),
        private val transactionsFailure: Throwable? = null,
    ) : TransactionApi {
        var lastPage: Int? = null
        var lastLimit: Int? = null
        var lastFrom: String? = null
        var lastTo: String? = null

        override suspend fun createTransaction(
            request: CreateTransactionRequest,
        ): Response<TransactionDetailResponse> = Response.success(detailResponse)

        override suspend fun getTransactions(
            page: Int,
            limit: Int,
            from: String?,
            to: String?,
        ): TransactionsResponse {
            lastPage = page
            lastLimit = limit
            lastFrom = from
            lastTo = to
            transactionsFailure?.let { throw it }
            return transactionsResponse
        }

        override suspend fun createV1Transaction(
            request: V1CreateTransactionRequest,
        ): Response<TransactionDetailResponse> = Response.success(detailResponse)

        override suspend fun getTransactionDetail(
            transactionId: String,
        ): TransactionDetailResponse = detailResponse
    }

    private companion object {
        const val TRANSACTION_ID = "transaction-id"

        fun summaryResponse() = TransactionResponse(
            transactionId = TRANSACTION_ID,
            clientTransactionId = "client-id",
            outletId = "outlet-id",
            userId = "user-id",
            customerId = null,
            status = "COMPLETED",
            subtotal = 25_000L,
            discount = 0L,
            tax = 0L,
            total = 25_000L,
            createdAt = "2026-09-23T10:00:00.000Z",
        )

        fun detailResponse() = TransactionDetailResponse(
            transactionId = TRANSACTION_ID,
            clientTransactionId = "client-id",
            outletId = "outlet-id",
            userId = "user-id",
            customerId = null,
            status = "COMPLETED",
            subtotal = 25_000L,
            discount = 0L,
            tax = 0L,
            total = 25_000L,
            items = listOf(
                TransactionItemResponse(
                    id = "item-id",
                    productId = "product-id",
                    quantity = 2,
                    unitPrice = 12_500L,
                    subtotal = 25_000L,
                ),
            ),
            payments = listOf(
                PaymentResponse(
                    id = "payment-id",
                    method = "CASH",
                    status = "PAID",
                    amount = 30_000L,
                    paidAt = "2026-09-23T10:00:00.000Z",
                ),
            ),
            change = 5_000L,
            createdAt = "2026-09-23T10:00:00.000Z",
        )
    }
}
