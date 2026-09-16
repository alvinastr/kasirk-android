package com.kasirkita.pos.di

import com.kasirkita.pos.data.repository.AuthRepositoryImpl
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.data.repository.ProductRepositoryImpl
import com.kasirkita.pos.data.repository.ShiftRepositoryImpl
import com.kasirkita.pos.domain.repository.AuthRepository
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
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

    @Binds
    @Singleton
    abstract fun bindCartRepository(
        implementation: CartRepositoryImpl,
    ): CartRepository

    @Binds
    @Singleton
    abstract fun bindShiftRepository(
        implementation: ShiftRepositoryImpl,
    ): ShiftRepository
}
