package com.kasirkita.pos.core.sync

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OfflineSyncSchedulerTest {

    @Test
    fun workRequest_isAccountScopedAndRequiresConnectivity() {
        val request = OfflineSyncScheduler.workRequest(TENANT_ID, USER_ID)

        assertEquals(TENANT_ID, request.workSpec.input.getString(OfflineSyncWorkContract.TENANT_ID))
        assertEquals(USER_ID, request.workSpec.input.getString(OfflineSyncWorkContract.USER_ID))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, request.workSpec.backoffDelayDuration)
    }

    @Test
    fun uniqueWorkName_changesForEachAccount() {
        val first = OfflineSyncScheduler.uniqueWorkName(TENANT_ID, USER_ID)
        val otherTenant = OfflineSyncScheduler.uniqueWorkName(OTHER_TENANT_ID, USER_ID)
        val otherUser = OfflineSyncScheduler.uniqueWorkName(TENANT_ID, OTHER_USER_ID)

        assertNotEquals(first, otherTenant)
        assertNotEquals(first, otherUser)
    }

    private companion object {
        const val TENANT_ID = "tenant-id"
        const val USER_ID = "user-id"
        const val OTHER_TENANT_ID = "other-tenant-id"
        const val OTHER_USER_ID = "other-user-id"
    }
}
