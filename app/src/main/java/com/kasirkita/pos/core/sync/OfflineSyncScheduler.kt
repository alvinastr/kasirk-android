package com.kasirkita.pos.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.SessionIdentity
import com.kasirkita.pos.core.datastore.toSessionIdentity
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class OfflineSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val authSessionDataStore: AuthSessionDataStore,
    private val offlineSyncRepository: OfflineSyncRepository,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        scope.launch { observeSessionBoundaries() }
        scope.launch { observePendingTransactions() }
    }

    fun enqueue(tenantId: String, userId: String) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(tenantId, userId),
            ExistingWorkPolicy.KEEP,
            workRequest(tenantId, userId),
        )
    }

    private suspend fun observeSessionBoundaries() {
        var previousIdentity: SessionIdentity? = null
        authSessionDataStore.sessionFlow
            .map { session -> session?.toSessionIdentity() }
            .distinctUntilChanged()
            .collect { activeIdentity ->
                if (previousIdentity != null && previousIdentity != activeIdentity) {
                    cancel(requireNotNull(previousIdentity))
                }
                previousIdentity = activeIdentity
            }
    }

    private suspend fun observePendingTransactions() {
        offlineSyncRepository.observeQueueSummary().collect { summary ->
            if (summary.pendingCount == 0) return@collect
            authSessionDataStore.getSession()
                ?.toSessionIdentity()
                ?.let { identity -> enqueue(identity.tenantId, identity.userId) }
        }
    }

    private fun cancel(identity: SessionIdentity) {
        workManager.cancelUniqueWork(uniqueWorkName(identity.tenantId, identity.userId))
    }

    internal companion object {
        fun uniqueWorkName(tenantId: String, userId: String): String =
            "${OfflineSyncWorkContract.UNIQUE_WORK_PREFIX}:$tenantId:$userId"

        fun workRequest(tenantId: String, userId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setInputData(
                    workDataOf(
                        OfflineSyncWorkContract.TENANT_ID to tenantId,
                        OfflineSyncWorkContract.USER_ID to userId,
                    ),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()
    }
}
