package com.kasirkita.pos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kasirkita.pos.core.database.entity.OfflineTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: OfflineTransactionEntity): Long

    @Query(
        """
        SELECT * FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND clientTransactionId = :clientTransactionId
        LIMIT 1
        """,
    )
    suspend fun getByClientTransactionId(
        tenantId: String,
        userId: String,
        clientTransactionId: String,
    ): OfflineTransactionEntity?

    @Query(
        """
        SELECT * FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status IN ('PENDING', 'RETRYABLE')
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingTransactions(
        tenantId: String,
        userId: String,
        limit: Int,
    ): List<OfflineTransactionEntity>

    @Query(
        """
        SELECT * FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status = 'FAILED'
        ORDER BY updatedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getFailedTransactions(
        tenantId: String,
        userId: String,
        limit: Int,
    ): List<OfflineTransactionEntity>

    @Query(
        """
        SELECT * FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status IN ('FAILED', 'RECONCILIATION_REQUIRED')
        ORDER BY updatedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getActionRequiredTransactions(
        tenantId: String,
        userId: String,
        limit: Int,
    ): List<OfflineTransactionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status IN ('PENDING', 'RETRYABLE')
        """,
    )
    fun observePendingCount(tenantId: String, userId: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status = :status
        """,
    )
    fun observeCountByStatus(
        tenantId: String,
        userId: String,
        status: String,
    ): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status IN ('FAILED', 'RECONCILIATION_REQUIRED')
        """,
    )
    fun observeActionRequiredCount(tenantId: String, userId: String): Flow<Int>

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'SYNCED',
            serverTransactionId = :serverTransactionId,
            lastError = NULL,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND clientTransactionId = :clientTransactionId
        """,
    )
    suspend fun markSynced(
        tenantId: String,
        userId: String,
        clientTransactionId: String,
        serverTransactionId: String,
    )

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'FAILED',
            lastError = :error,
            retryCount = retryCount + 1,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND clientTransactionId = :clientTransactionId
        """,
    )
    suspend fun markFailed(
        tenantId: String,
        userId: String,
        clientTransactionId: String,
        error: String,
    )

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'RECONCILIATION_REQUIRED',
            serverTransactionId = :serverTransactionId,
            lastError = :error,
            retryCount = retryCount + 1,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND clientTransactionId = :clientTransactionId
        """,
    )
    suspend fun markReconciliationRequired(
        tenantId: String,
        userId: String,
        clientTransactionId: String,
        serverTransactionId: String,
        error: String,
    )

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'RETRYABLE',
            lastError = :error,
            retryCount = retryCount + 1,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND clientTransactionId IN (:clientTransactionIds)
          AND status IN ('PENDING', 'RETRYABLE')
        """,
    )
    suspend fun markRetryable(
        tenantId: String,
        userId: String,
        clientTransactionIds: List<String>,
        error: String,
    )

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'PENDING',
            lastError = NULL,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status = 'FAILED'
        """,
    )
    suspend fun retryFailedTransactions(tenantId: String, userId: String)

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'PENDING',
            lastError = NULL,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND clientTransactionId = :clientTransactionId
          AND status = 'FAILED'
        """,
    )
    suspend fun retryFailedTransaction(
        tenantId: String,
        userId: String,
        clientTransactionId: String,
    )

    @Query(
        """
        DELETE FROM offline_transactions
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND status = 'SYNCED'
        """,
    )
    suspend fun deleteSynced(tenantId: String, userId: String)
}
