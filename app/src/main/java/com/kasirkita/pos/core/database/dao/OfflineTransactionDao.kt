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
        WHERE clientTransactionId = :clientTransactionId
        LIMIT 1
        """,
    )
    suspend fun getByClientTransactionId(
        clientTransactionId: String,
    ): OfflineTransactionEntity?

    @Query(
        """
        SELECT * FROM offline_transactions
        WHERE status = 'PENDING'
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingTransactions(limit: Int): List<OfflineTransactionEntity>

    @Query("SELECT COUNT(*) FROM offline_transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'SYNCED',
            serverTransactionId = :serverTransactionId,
            lastError = NULL,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE clientTransactionId = :clientTransactionId
        """,
    )
    suspend fun markSynced(
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
        WHERE clientTransactionId = :clientTransactionId
        """,
    )
    suspend fun markFailed(
        clientTransactionId: String,
        error: String,
    )

    @Query(
        """
        UPDATE offline_transactions
        SET retryCount = retryCount + 1,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE clientTransactionId IN (:clientTransactionIds)
          AND status = 'PENDING'
        """,
    )
    suspend fun incrementRetry(clientTransactionIds: List<String>)

    @Query(
        """
        UPDATE offline_transactions
        SET status = 'PENDING',
            lastError = NULL,
            updatedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000
        WHERE status = 'FAILED'
        """,
    )
    suspend fun retryFailedTransactions()

    @Query("DELETE FROM offline_transactions WHERE status = 'SYNCED'")
    suspend fun deleteSynced()
}
