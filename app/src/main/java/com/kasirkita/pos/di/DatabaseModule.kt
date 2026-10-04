package com.kasirkita.pos.di

import android.content.Context
import androidx.room.Room
import com.kasirkita.pos.core.database.AppDatabase
import com.kasirkita.pos.core.database.dao.CategoryDao
import com.kasirkita.pos.core.database.dao.ModifierGroupDao
import com.kasirkita.pos.core.database.dao.ModifierOptionDao
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.dao.ProductModifierGroupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
        )
        .build()

    @Provides
    @Singleton
    fun provideProductDao(database: AppDatabase): ProductDao = database.productDao()

    @Provides
    @Singleton
    fun provideOfflineTransactionDao(
        database: AppDatabase,
    ): OfflineTransactionDao = database.offlineTransactionDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    @Singleton
    fun provideModifierGroupDao(database: AppDatabase): ModifierGroupDao =
        database.modifierGroupDao()

    @Provides
    @Singleton
    fun provideModifierOptionDao(database: AppDatabase): ModifierOptionDao =
        database.modifierOptionDao()

    @Provides
    @Singleton
    fun provideProductModifierGroupDao(database: AppDatabase): ProductModifierGroupDao =
        database.productModifierGroupDao()

    private const val DATABASE_NAME = "kasirkita.db"
}
