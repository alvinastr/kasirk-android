package com.kasirkita.pos.core.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import com.kasirkita.pos.core.database.entity.ProductEntity
import com.kasirkita.pos.domain.model.OfflineTransactionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun migrate1To4_preservesProductsAndCreatesAccountScopedOfflineQueue() = runBlocking {
        createVersionOneDatabase()

        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME,
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            )
            .build()

        val migrated = requireNotNull(database)
        assertTrue(migrated.productDao().getProducts(TENANT_ID).single().trackStock)
        assertTrue(
            migrated.offlineTransactionDao()
                .getPendingTransactions(TENANT_ID, USER_ID, 100)
                .isEmpty(),
        )
    }

    @Test
    fun migrate2To4_existingProductDefaultsToTrackedStock() = runBlocking {
        createVersionTwoDatabase()

        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME,
        )
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        val product = requireNotNull(database).productDao().getProducts(TENANT_ID).single()

        assertTrue(product.trackStock)
    }

    @Test
    fun migrate3To4_preservesProductsAndDiscardsUnownedOfflineRows() = runBlocking {
        createVersionThreeDatabase()

        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME,
        )
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()

        val migrated = requireNotNull(database)
        assertEquals(1, migrated.productDao().getProducts(TENANT_ID).size)
        assertTrue(
            migrated.offlineTransactionDao()
                .getPendingTransactions(TENANT_ID, USER_ID, 100)
                .isEmpty(),
        )
    }

    @Test
    fun migrate4To5_preservesExistingDataAndCreatesMetadataTables() = runBlocking {
        createVersionFourDatabase()

        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME,
        )
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .build()

        val migrated = requireNotNull(database)
        val product = migrated.productDao().getProducts(TENANT_ID).single()
        val offlineTransaction = migrated.offlineTransactionDao()
            .getPendingTransactions(TENANT_ID, USER_ID, 100)
            .single()

        assertEquals("legacy-product", product.id)
        assertFalse(product.modifierMetadataLoaded)
        assertEquals(LEGACY_PAYLOAD_JSON, offlineTransaction.payloadJson)
        assertEquals("legacy-client", offlineTransaction.clientTransactionId)

        val sqlite = migrated.openHelper.writableDatabase
        listOf(
            "categories",
            "modifier_groups",
            "modifier_options",
            "product_modifier_groups",
        ).forEach { tableName ->
            sqlite.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(tableName),
            ).use { cursor -> assertTrue(cursor.moveToFirst()) }
        }
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
        val cached = requireNotNull(database).productDao().getProducts(TENANT_ID).single()

        assertFalse(cached.trackStock)
    }

    @Test
    fun currentSchema_isolatesProductsByTenant() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).build()
        val firstTenantProduct = product(id = "shared-product", tenantId = TENANT_ID)
        val secondTenantProduct = product(id = "shared-product", tenantId = OTHER_TENANT_ID)

        requireNotNull(database).productDao().insertProducts(
            listOf(firstTenantProduct, secondTenantProduct),
        )

        assertEquals(
            listOf(TENANT_ID),
            requireNotNull(database).productDao()
                .getProducts(TENANT_ID)
                .map(ProductEntity::tenantId),
        )
        assertEquals(
            listOf(OTHER_TENANT_ID),
            requireNotNull(database).productDao()
                .getProducts(OTHER_TENANT_ID)
                .map(ProductEntity::tenantId),
        )
    }

    @Test
    fun currentSchema_isolatesOfflineTransactionsByTenantAndUser() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).build()
        val dao = requireNotNull(database).offlineTransactionDao()
        dao.insert(offlineTransaction(TENANT_ID, USER_ID))
        dao.insert(offlineTransaction(TENANT_ID, OTHER_USER_ID))
        dao.insert(offlineTransaction(OTHER_TENANT_ID, USER_ID))
        dao.markFailed(TENANT_ID, USER_ID, "shared-client-id", "needs action")
        dao.markFailed(TENANT_ID, OTHER_USER_ID, "shared-client-id", "other user")
        dao.markFailed(OTHER_TENANT_ID, USER_ID, "shared-client-id", "other tenant")

        assertEquals(0, dao.getPendingTransactions(TENANT_ID, USER_ID, 100).size)
        assertEquals(1, dao.getFailedTransactions(TENANT_ID, USER_ID, 100).size)
        assertEquals(
            1,
            dao.observeCountByStatus(TENANT_ID, USER_ID, OfflineTransactionStatus.FAILED).first(),
        )
        assertEquals(0, dao.observePendingCount(TENANT_ID, OTHER_USER_ID).first())
        assertEquals(0, dao.getPendingTransactions(OTHER_TENANT_ID, USER_ID, 100).size)

        dao.retryFailedTransaction(TENANT_ID, USER_ID, "shared-client-id")

        assertEquals(1, dao.getPendingTransactions(TENANT_ID, USER_ID, 100).size)
        assertEquals(0, dao.getFailedTransactions(TENANT_ID, USER_ID, 100).size)
        assertEquals(1, dao.getFailedTransactions(TENANT_ID, OTHER_USER_ID, 100).size)
        assertEquals(1, dao.getFailedTransactions(OTHER_TENANT_ID, USER_ID, 100).size)
    }

    private fun createVersionOneDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
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
                            INSERT INTO `products` (
                                `id`, `tenantId`, `categoryId`, `name`, `sku`,
                                `price`, `cost`, `minimumStock`, `isActive`, `createdAt`
                            ) VALUES (
                                'legacy-product', '$TENANT_ID', NULL, 'Legacy Product', 'LEGACY',
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

    private fun createVersionThreeDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
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
                                `trackStock` INTEGER NOT NULL DEFAULT 1,
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
                                `id`, `tenantId`, `categoryId`, `name`, `sku`, `price`,
                                `cost`, `minimumStock`, `isActive`, `createdAt`, `trackStock`
                            ) VALUES (
                                'legacy-product', '$TENANT_ID', NULL, 'Legacy Product', 'LEGACY',
                                10000, 5000, 0, 1, '2026-09-18T00:00:00.000Z', 1
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            INSERT INTO `offline_transactions` (
                                `id`, `clientTransactionId`, `outletId`, `payloadJson`, `status`,
                                `serverTransactionId`, `lastError`, `retryCount`, `createdAt`, `updatedAt`
                            ) VALUES (
                                'legacy-offline', 'legacy-client', 'outlet-id', '{}', 'PENDING',
                                NULL, NULL, 0, 1, 1
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

    private fun createVersionFourDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE `products` (
                            `id` TEXT NOT NULL, `tenantId` TEXT NOT NULL, `categoryId` TEXT,
                            `name` TEXT NOT NULL, `sku` TEXT NOT NULL, `price` INTEGER NOT NULL,
                            `cost` INTEGER NOT NULL, `minimumStock` INTEGER NOT NULL,
                            `trackStock` INTEGER NOT NULL DEFAULT 1, `isActive` INTEGER NOT NULL,
                            `createdAt` TEXT NOT NULL, PRIMARY KEY(`tenantId`, `id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE `offline_transactions` (
                            `id` TEXT NOT NULL, `tenantId` TEXT NOT NULL, `userId` TEXT NOT NULL,
                            `clientTransactionId` TEXT NOT NULL, `outletId` TEXT NOT NULL,
                            `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL,
                            `serverTransactionId` TEXT, `lastError` TEXT,
                            `retryCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`tenantId`, `userId`, `clientTransactionId`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE INDEX `index_offline_transactions_tenantId_userId_status_createdAt`
                        ON `offline_transactions` (`tenantId`, `userId`, `status`, `createdAt`)
                    """.trimIndent())
                    db.execSQL(
                        "INSERT INTO products (id, tenantId, categoryId, name, sku, price, " +
                            "cost, minimumStock, trackStock, isActive, createdAt) " +
                            "VALUES ('legacy-product', '$TENANT_ID', NULL, 'Legacy Product', " +
                            "'LEGACY', 10000, 5000, 0, 1, 1, '2026-09-18T00:00:00.000Z')",
                    )
                    db.execSQL(
                        "INSERT INTO offline_transactions (id, tenantId, userId, " +
                            "clientTransactionId, outletId, payloadJson, status, " +
                            "serverTransactionId, lastError, retryCount, createdAt, updatedAt) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, 0, 1, 1)",
                        arrayOf(
                            "legacy-offline", TENANT_ID, USER_ID, "legacy-client",
                            "outlet-id", LEGACY_PAYLOAD_JSON, "PENDING",
                        ),
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .use { helper -> helper.writableDatabase }
    }

    private fun product(id: String, tenantId: String) = ProductEntity(
        id = id,
        tenantId = tenantId,
        categoryId = null,
        name = "Product $tenantId",
        sku = "SKU-$tenantId",
        price = 10_000L,
        cost = 5_000L,
        minimumStock = 0,
        trackStock = true,
        isActive = true,
        createdAt = "2026-09-19T00:00:00.000Z",
    )

    private fun offlineTransaction(
        tenantId: String,
        userId: String,
    ) = OfflineTransactionEntity(
        id = "row-$tenantId-$userId",
        tenantId = tenantId,
        userId = userId,
        clientTransactionId = "shared-client-id",
        outletId = "outlet-id",
        payloadJson = "{}",
        status = OfflineTransactionStatus.PENDING,
        serverTransactionId = null,
        lastError = null,
        retryCount = 0,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private companion object {
        const val TEST_DATABASE_NAME = "track-stock-migration-test.db"
        const val TENANT_ID = "tenant-id"
        const val OTHER_TENANT_ID = "other-tenant-id"
        const val USER_ID = "user-id"
        const val OTHER_USER_ID = "other-user-id"
        const val LEGACY_PAYLOAD_JSON = "{}"
    }
}
