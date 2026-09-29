package com.kasirkita.pos.core.sync

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.SessionIdentity
import com.kasirkita.pos.core.datastore.toSessionIdentity
import com.kasirkita.pos.domain.model.SyncOutcome
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import javax.inject.Inject

class OfflineSyncWorkRunner @Inject constructor(
    private val authSessionDataStore: AuthSessionDataStore,
    private val offlineSyncRepository: OfflineSyncRepository,
) {
    suspend fun run(tenantId: String?, userId: String?): OfflineSyncWorkResult {
        val expectedIdentity = SessionIdentity(
            tenantId = tenantId?.takeIf(String::isNotBlank)
                ?: return OfflineSyncWorkResult.FAILURE,
            userId = userId?.takeIf(String::isNotBlank)
                ?: return OfflineSyncWorkResult.FAILURE,
        )
        val activeIdentity = authSessionDataStore.getSession()?.toSessionIdentity()
        if (activeIdentity != expectedIdentity) {
            return OfflineSyncWorkResult.SUCCESS
        }

        return offlineSyncRepository.syncPendingTransactionsForAccount(
            tenantId = expectedIdentity.tenantId,
            userId = expectedIdentity.userId,
        ).fold(
            onSuccess = { outcome ->
                when (outcome) {
                    is SyncOutcome.RetryableFailure -> OfflineSyncWorkResult.RETRY
                    is SyncOutcome.Completed,
                    is SyncOutcome.ActionRequired,
                    SyncOutcome.AuthenticationUnavailable,
                    -> OfflineSyncWorkResult.SUCCESS
                }
            },
            onFailure = { OfflineSyncWorkResult.FAILURE },
        )
    }
}

enum class OfflineSyncWorkResult {
    SUCCESS,
    RETRY,
    FAILURE,
}
