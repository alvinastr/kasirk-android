package com.kasirkita.pos.domain.usecase

import android.util.Log
import com.kasirkita.pos.BuildConfig
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.repository.ReceiptRepository
import javax.inject.Inject

class GetReceiptUseCase @Inject constructor(
    private val repository: ReceiptRepository,
) {
    suspend operator fun invoke(transactionId: String): Result<Receipt> {
        val startedNanos = System.nanoTime()
        val result = repository.getReceipt(transactionId)
        if (BuildConfig.DEBUG) {
            runCatching {
                Log.d(
                    PRINT_TIMING_TAG,
                    "receipt_retrieval_ms=${millisSince(startedNanos)} success=${result.isSuccess}",
                )
            }
        }
        return result
    }

    private fun millisSince(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val PRINT_TIMING_TAG = "PrintTiming"
    }
}
