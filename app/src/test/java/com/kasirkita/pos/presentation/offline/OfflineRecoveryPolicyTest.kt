package com.kasirkita.pos.presentation.offline

import com.kasirkita.pos.domain.model.OfflineTransactionFailureType
import org.junit.Assert.assertEquals
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
    fun rejectedTransaction_hasNoManualActionDuringIsolation() {
        val actions = recoveryActionsFor(OfflineTransactionFailureType.REJECTED)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun reconciliationTransaction_hasNoManualActionDuringIsolation() {
        val actions = recoveryActionsFor(
            OfflineTransactionFailureType.RECONCILIATION_REQUIRED,
        )

        assertTrue(actions.isEmpty())
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
