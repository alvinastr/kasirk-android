package com.kasirkita.pos.core.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kasirkita.pos.core.database.entity.ProductEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun migrate2To3_existingProductDefaultsToTrackedStock() = runBlocking {
        createVersionTwoDatabase()

        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME,
        )
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()

        val product = requireNotNull(database).productDao().getProducts().single()

        assertTrue(product.trackStock)
    }

    @Test
    fun currentSchema_persistsUntrackedProduct() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).build()
        val product = ProductEntity(
            id = "untracked-product",
            tenantId = "tenant-id",
            categoryId = null,
            name = "Jasa Antar",
            sku = "JASA-ANTAR",
            price = 10_000L,
            cost = 0L,
            minimumStock = 0,
            trackStock = false,
            isActive = true,
            createdAt = "2026-09-19T00:00:00.000Z",
        )

        requireNotNull(database).productDao().insertProducts(listOf(product))
        val cached = requireNotNull(database).productDao().getProducts().single()

        assertFalse(cached.trackStock)
    }

    private fun createVersionTwoDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `products` (
                                `id` TEXT NOT NULL,
                                `tenantId` TEXT NOT NULL,
                                `categoryId` TEXT,
                                `name` TEXT NOT NULL,
                                `sku` TEXT NOT NULL,
                                `price` INTEGER NOT NULL,
                                `cost` INTEGER NOT NULL,
                                `minimumStock` INTEGER NOT NULL,
                                `isActive` INTEGER NOT NULL,
                                `createdAt` TEXT NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent(),
                        )
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
                        db.execSQL(
                            """
                            INSERT INTO `products` (
                                `id`, `tenantId`, `categoryId`, `name`, `sku`,
                                `price`, `cost`, `minimumStock`, `isActive`, `createdAt`
                            ) VALUES (
                                'legacy-product', 'tenant-id', NULL, 'Legacy Product', 'LEGACY',
                                10000, 5000, 0, 1, '2026-09-18T00:00:00.000Z'
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()

        FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .use { helper -> helper.writableDatabase }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "track-stock-migration-test.db"
    }
}
