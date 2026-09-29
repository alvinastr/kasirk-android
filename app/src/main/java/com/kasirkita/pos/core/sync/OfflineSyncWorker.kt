package com.kasirkita.pos.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val runner: OfflineSyncWorkRunner,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = when (
        runner.run(
            tenantId = inputData.getString(OfflineSyncWorkContract.TENANT_ID),
            userId = inputData.getString(OfflineSyncWorkContract.USER_ID),
        )
    ) {
        OfflineSyncWorkResult.SUCCESS -> Result.success()
        OfflineSyncWorkResult.RETRY -> Result.retry()
        OfflineSyncWorkResult.FAILURE -> Result.failure()
    }
}
