package com.kasirkita.pos.di

import com.kasirkita.pos.core.datastore.LegacySessionCleaner
import com.kasirkita.pos.core.datastore.LegacySessionReader
import com.kasirkita.pos.core.datastore.TokenDataStoreLegacySessionReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    @Singleton
    abstract fun bindLegacySessionReader(
        implementation: TokenDataStoreLegacySessionReader,
    ): LegacySessionReader

    @Binds
    @Singleton
    abstract fun bindLegacySessionCleaner(
        implementation: TokenDataStoreLegacySessionReader,
    ): LegacySessionCleaner
}
