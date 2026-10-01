package com.kasirkita.pos.di

import com.kasirkita.pos.core.security.AndroidKeystoreAesGcm
import com.kasirkita.pos.core.security.SecureTokenStorage
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
}
