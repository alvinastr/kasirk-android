package com.kasirkita.pos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import com.kasirkita.pos.core.database.entity.ProductEntity

@Database(
    entities = [
        ProductEntity::class,
        OfflineTransactionEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    abstract fun offlineTransactionDao(): OfflineTransactionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_transactions` (
                        `id` TEXT NOT NULL,
                        `clientTransactionId` TEXT NOT NULL,
                        `outletId` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `serverTransactionId` TEXT,
                        `lastError` TEXT,
                        `retryCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_offline_transactions_clientTransactionId`
                    ON `offline_transactions` (`clientTransactionId`)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `products`
                    ADD COLUMN `trackStock` INTEGER NOT NULL DEFAULT 1
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `products_v4` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `categoryId` TEXT,
                        `name` TEXT NOT NULL,
                        `sku` TEXT NOT NULL,
                        `price` INTEGER NOT NULL,
                        `cost` INTEGER NOT NULL,
                        `minimumStock` INTEGER NOT NULL,
                        `trackStock` INTEGER NOT NULL DEFAULT 1,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        PRIMARY KEY(`tenantId`, `id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `products_v4` (
                        `id`, `tenantId`, `categoryId`, `name`, `sku`, `price`,
                        `cost`, `minimumStock`, `trackStock`, `isActive`, `createdAt`
                    )
                    SELECT
                        `id`, `tenantId`, `categoryId`, `name`, `sku`, `price`,
                        `cost`, `minimumStock`, `trackStock`, `isActive`, `createdAt`
                    FROM `products`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `products`")
                db.execSQL("ALTER TABLE `products_v4` RENAME TO `products`")

                db.execSQL(
                    """
                    CREATE TABLE `offline_transactions_v4` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `clientTransactionId` TEXT NOT NULL,
                        `outletId` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `serverTransactionId` TEXT,
                        `lastError` TEXT,
                        `retryCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tenantId`, `userId`, `clientTransactionId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `offline_transactions`")
                db.execSQL(
                    "ALTER TABLE `offline_transactions_v4` RENAME TO `offline_transactions`",
                )
                db.execSQL(
                    """
                    CREATE INDEX `index_offline_transactions_tenantId_userId_status_createdAt`
                    ON `offline_transactions` (`tenantId`, `userId`, `status`, `createdAt`)
                    """.trimIndent(),
                )
            }
        }
    }
}
