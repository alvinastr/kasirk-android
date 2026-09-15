package com.kasirkita.pos.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides application-scoped networking dependencies.
 * Retrofit providers will be added when the API contract is implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
