package com.kasirkita.pos.di

import com.kasirkita.pos.data.repository.AuthRepositoryImpl
import com.kasirkita.pos.data.repository.ProductRepositoryImpl
import com.kasirkita.pos.domain.repository.AuthRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        implementation: AuthRepositoryImpl,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        implementation: ProductRepositoryImpl,
    ): ProductRepository
}
