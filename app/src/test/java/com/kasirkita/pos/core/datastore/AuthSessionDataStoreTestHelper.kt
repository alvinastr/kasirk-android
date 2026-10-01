package com.kasirkita.pos.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kasirkita.pos.core.security.FakeSecureTokenStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AuthSessionDataStoreTestHelper {
    fun createTestStore(
        dataStore: DataStore<Preferences> = InMemoryPreferencesDataStore(),
    ): AuthSessionDataStore {
        return AuthSessionDataStore(
            dataStore = dataStore,
            secureTokenStorage = FakeSecureTokenStorage(),
        )
    }
}

class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            transform(state.value).also { state.value = it }
        }
}
