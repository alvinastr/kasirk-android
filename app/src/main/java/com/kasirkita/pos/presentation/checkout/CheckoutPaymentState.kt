package com.kasirkita.pos.presentation.checkout

import com.kasirkita.pos.domain.model.V1Payment

internal const val V1_PAYMENT_METHOD_CASH = "CASH"
internal const val V1_PAYMENT_METHOD_QRIS = "QRIS"
internal const val MAX_QUICK_TENDER_CANDIDATES = 4

enum class CheckoutPaymentMethod {
    CASH,
    QRIS,
}

enum class CashTenderMode {
    NONE,
    EXACT,
    QUICK,
    MANUAL,
}

data class CheckoutPaymentState(
    val totalAmount: Long = 0L,
    val method: CheckoutPaymentMethod = CheckoutPaymentMethod.CASH,
    val amountReceived: Long? = null,
    val tenderMode: CashTenderMode = CashTenderMode.NONE,
    val manualInput: String = "",
) {
    val quickTenderCandidates: List<Long> = quickTenderCandidates(totalAmount)

    val changeAmount: Long
        get() = if (method == CheckoutPaymentMethod.CASH) {
            val received = amountReceived ?: 0L
            if (received >= totalAmount) received - totalAmount else 0L
        } else {
            0L
        }

    val shortageAmount: Long
        get() = if (method == CheckoutPaymentMethod.CASH) {
            val received = amountReceived ?: 0L
            if (received < totalAmount) totalAmount - received else 0L
        } else {
            0L
        }

    val canSubmit: Boolean
        get() = when (method) {
            CheckoutPaymentMethod.CASH -> amountReceived != null && amountReceived >= totalAmount
            CheckoutPaymentMethod.QRIS -> totalAmount >= 0L
        }

    fun withTotal(total: Long): CheckoutPaymentState = copy(totalAmount = total.coerceAtLeast(0L))

    fun selectCash(): CheckoutPaymentState = copy(
        method = CheckoutPaymentMethod.CASH,
        amountReceived = null,
        tenderMode = CashTenderMode.NONE,
        manualInput = "",
    )

    fun selectQris(): CheckoutPaymentState = copy(
        method = CheckoutPaymentMethod.QRIS,
        amountReceived = null,
        tenderMode = CashTenderMode.NONE,
        manualInput = "",
    )

    fun selectExactCash(): CheckoutPaymentState = copy(
        method = CheckoutPaymentMethod.CASH,
        amountReceived = totalAmount,
        tenderMode = CashTenderMode.EXACT,
        manualInput = totalAmount.toString(),
    )

    fun selectTender(amount: Long): CheckoutPaymentState = copy(
        method = CheckoutPaymentMethod.CASH,
        amountReceived = amount.takeIf { it >= 0L },
        tenderMode = CashTenderMode.QUICK,
        manualInput = amount.takeIf { it >= 0L }?.toString().orEmpty(),
    )

    fun selectManualCash(): CheckoutPaymentState = copy(
        method = CheckoutPaymentMethod.CASH,
        tenderMode = CashTenderMode.MANUAL,
    )

    fun enterManualCash(input: String): CheckoutPaymentState {
        val parsed = parseManualRupiah(input)
        return copy(
            method = CheckoutPaymentMethod.CASH,
            amountReceived = parsed,
            tenderMode = CashTenderMode.MANUAL,
            manualInput = input,
        )
    }

    fun toPayment(): V1Payment = when (method) {
        CheckoutPaymentMethod.CASH -> V1Payment(V1_PAYMENT_METHOD_CASH, amountReceived)
        CheckoutPaymentMethod.QRIS -> V1Payment(V1_PAYMENT_METHOD_QRIS, null)
    }
}

fun quickTenderCandidates(totalAmount: Long): List<Long> {
    if (totalAmount <= 0L) return emptyList()

    val candidates = linkedSetOf<Long>()
    val commonDenominations = listOf(1_000L, 2_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
    commonDenominations
        .asSequence()
        .filter { it > totalAmount }
        .forEach(candidates::add)

    if (totalAmount >= 100_000L) {
        listOf(50_000L, 100_000L).forEach { unit ->
            roundUpStrict(totalAmount, unit)?.let(candidates::add)
        }
    }

    return candidates
        .asSequence()
        .filter { it >= totalAmount && it >= 0L }
        .distinct()
        .sorted()
        .take(MAX_QUICK_TENDER_CANDIDATES)
        .toList()
}

private fun roundUpStrict(value: Long, unit: Long): Long? {
    if (value < 0L || unit <= 0L) return null
    val quotient = Math.floorDiv(value, unit)
    val base = runCatching { Math.multiplyExact(quotient, unit) }.getOrNull() ?: return null
    val next = if (base > value) base else runCatching { Math.addExact(base, unit) }.getOrNull() ?: return null
    return next.takeIf { it >= 0L }
}

private fun parseManualRupiah(input: String): Long? {
    if (input.isBlank()) return null
    if (!input.all(Char::isDigit)) return null
    return input.toLongOrNull()?.takeIf { it >= 0L }
}
