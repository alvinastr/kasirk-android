package com.kasirkita.pos.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides application-scoped persistence dependencies.
 * Room database and DAO providers will be added with the local data layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule
