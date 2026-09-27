package com.kasirkita.pos.core.datastore

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProvider @Inject constructor(
    private val authSessionDataStore: AuthSessionDataStore,
) {
    suspend fun getDeviceId(): String {
        authSessionDataStore.getStoredDeviceId()?.let { storedDeviceId ->
            return storedDeviceId
        }

        return authSessionDataStore.saveDeviceIdIfAbsent(UUID.randomUUID().toString())
    }
}
