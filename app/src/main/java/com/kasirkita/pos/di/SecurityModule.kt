package com.kasirkita.pos.di

import com.kasirkita.pos.core.security.AndroidKeystoreAesGcm
import com.kasirkita.pos.core.security.SecureTokenStorage
import com.kasirkita.pos.core.policy.OfflineQueueCreationPolicy
import com.kasirkita.pos.core.policy.LegacyOfflineQueuePolicy
import com.kasirkita.pos.core.policy.HeldOrderPilotPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideAndroidKeystoreAesGcm(): AndroidKeystoreAesGcm {
        return AndroidKeystoreAesGcm()
    }

    @Provides
    @Singleton
    fun provideSecureTokenStorage(
        crypto: AndroidKeystoreAesGcm,
    ): SecureTokenStorage {
        return SecureTokenStorage(crypto)
    }

    @Provides
    @Singleton
    fun provideOfflineQueueCreationPolicy(): OfflineQueueCreationPolicy =
        OfflineQueueCreationPolicy { false }

    @Provides
    @Singleton
    fun provideLegacyOfflineQueuePolicy(): LegacyOfflineQueuePolicy =
        LegacyOfflineQueuePolicy { false }

    @Provides
    @Singleton
    fun provideHeldOrderPilotPolicy(): HeldOrderPilotPolicy =
        HeldOrderPilotPolicy { true }
}
