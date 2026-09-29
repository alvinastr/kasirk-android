package com.kasirkita.pos.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckoutTransactionIdentityTest {

    @Test
    fun processDeathRecovery_reusesPersistedClientTransactionId() {
        val beforeDeath = SavedStateHandle()
        val firstIdentity = CheckoutTransactionIdentity(beforeDeath) { "stable-client-id" }
        val clientTransactionId = firstIdentity.getOrCreate()
        val restoredState = SavedStateHandle(
            mapOf(CheckoutTransactionIdentity.KEY to clientTransactionId),
        )
        val afterDeath = CheckoutTransactionIdentity(restoredState) { "different-id" }

        assertEquals("stable-client-id", afterDeath.getOrCreate())
        assertEquals("stable-client-id", afterDeath.getOrCreate())
    }
}
