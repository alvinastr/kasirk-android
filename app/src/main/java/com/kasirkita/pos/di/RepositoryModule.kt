package com.kasirkita.pos.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds repository implementations to their domain contracts.
 * Bindings will be added after the repository contracts and implementations exist.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
