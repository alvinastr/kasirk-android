package com.kasirkita.pos.presentation.offline

import com.kasirkita.pos.domain.model.OfflineTransactionFailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRecoveryPolicyTest {

    @Test
    fun retryableTransaction_hasNoManualAction() {
        assertTrue(
            recoveryActionsFor(OfflineTransactionFailureType.RETRYABLE).isEmpty(),
        )
    }

    @Test
    fun rejectedTransaction_canOnlyRetryOrDelete() {
        val actions = recoveryActionsFor(OfflineTransactionFailureType.REJECTED)

        assertEquals(
            setOf(OfflineRecoveryAction.RETRY, OfflineRecoveryAction.DELETE),
            actions,
        )
        assertFalse(OfflineRecoveryAction.ACKNOWLEDGE in actions)
    }

    @Test
    fun reconciliationTransaction_canOnlyBeAcknowledged() {
        val actions = recoveryActionsFor(
            OfflineTransactionFailureType.RECONCILIATION_REQUIRED,
        )

        assertEquals(setOf(OfflineRecoveryAction.ACKNOWLEDGE), actions)
        assertFalse(OfflineRecoveryAction.RETRY in actions)
        assertFalse(OfflineRecoveryAction.DELETE in actions)
    }

    @Test
    fun technicalErrors_areMappedToIndonesianMessages() {
        assertEquals(
            "Stok tidak mencukupi saat transaksi disinkronkan.",
            offlineRecoveryErrorMessage("INSUFFICIENT_STOCK: Insufficient stock"),
        )
        assertEquals(
            "Nilai transaksi di server berbeda dari nilai saat checkout.",
            offlineRecoveryErrorMessage(
                "Synced transaction does not match its checkout financial snapshot",
            ),
        )
        assertEquals(
            "Server belum dapat memproses sinkronisasi. Sistem akan mencoba lagi.",
            offlineRecoveryErrorMessage("Sync service returned an incomplete response"),
        )
    }
}
