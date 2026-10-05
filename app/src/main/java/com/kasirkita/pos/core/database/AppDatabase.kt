package com.kasirkita.pos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kasirkita.pos.core.database.dao.CategoryDao
import com.kasirkita.pos.core.database.dao.ModifierGroupDao
import com.kasirkita.pos.core.database.dao.ModifierOptionDao
import com.kasirkita.pos.core.database.dao.OfflineTransactionDao
import com.kasirkita.pos.core.database.dao.ProductDao
import com.kasirkita.pos.core.database.dao.ProductModifierGroupDao
import com.kasirkita.pos.core.database.entity.CategoryEntity
import com.kasirkita.pos.core.database.entity.ModifierGroupEntity
import com.kasirkita.pos.core.database.entity.ModifierOptionEntity
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import com.kasirkita.pos.core.database.entity.ProductEntity
import com.kasirkita.pos.core.database.entity.ProductModifierGroupEntity

@Database(
    entities = [
        ProductEntity::class,
        OfflineTransactionEntity::class,
        CategoryEntity::class,
        ModifierGroupEntity::class,
        ModifierOptionEntity::class,
        ProductModifierGroupEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun offlineTransactionDao(): OfflineTransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun modifierGroupDao(): ModifierGroupDao
    abstract fun modifierOptionDao(): ModifierOptionDao
    abstract fun productModifierGroupDao(): ProductModifierGroupDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add modifierMetadataLoaded column to products
                db.execSQL(
                    """
                    ALTER TABLE `products`
                    ADD COLUMN `modifierMetadataLoaded` INTEGER NOT NULL DEFAULT 0
                    """.trimIndent(),
                )

                // Create categories table
                db.execSQL(
                    """
                    CREATE TABLE `categories` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        PRIMARY KEY(`tenantId`, `id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX `index_categories_tenantId`
                    ON `categories` (`tenantId`)
                    """.trimIndent(),
                )

                // Create modifier_groups table
                db.execSQL(
                    """
                    CREATE TABLE `modifier_groups` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`tenantId`, `id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX `index_modifier_groups_tenantId`
                    ON `modifier_groups` (`tenantId`)
                    """.trimIndent(),
                )

                // Create modifier_options table
                db.execSQL(
                    """
                    CREATE TABLE `modifier_options` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `modifierGroupId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `priceDelta` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `displayOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`tenantId`, `id`),
                        FOREIGN KEY(`tenantId`, `modifierGroupId`) REFERENCES `modifier_groups`(`tenantId`, `id`) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX `index_modifier_options_tenantId_modifierGroupId`
                    ON `modifier_options` (`tenantId`, `modifierGroupId`)
                    """.trimIndent(),
                )

                // Create product_modifier_groups table
                db.execSQL(
                    """
                    CREATE TABLE `product_modifier_groups` (
                        `tenantId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `modifierGroupId` TEXT NOT NULL,
                        `required` INTEGER NOT NULL,
                        `selectionType` TEXT NOT NULL,
                        `displayOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`tenantId`, `productId`, `modifierGroupId`),
                        FOREIGN KEY(`tenantId`, `productId`) REFERENCES `products`(`tenantId`, `id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tenantId`, `modifierGroupId`) REFERENCES `modifier_groups`(`tenantId`, `id`) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX `index_product_modifier_groups_tenantId_productId`
                    ON `product_modifier_groups` (`tenantId`, `productId`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX `index_product_modifier_groups_tenantId_modifierGroupId`
                    ON `product_modifier_groups` (`tenantId`, `modifierGroupId`)
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
