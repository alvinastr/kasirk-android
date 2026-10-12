package com.kasirkita.pos.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kasirkita.pos.core.database.entity.OnlineTransactionJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineTransactionJournalDao {

    @Query(
        """
        SELECT * FROM online_transaction_journal
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND outletId = :outletId
          AND clientTransactionId = :clientTransactionId
        LIMIT 1
        """,
    )
    suspend fun get(
        tenantId: String,
        userId: String,
        outletId: String,
        clientTransactionId: String,
    ): OnlineTransactionJournalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: OnlineTransactionJournalEntity)

    @Transaction
    suspend fun insertIfAbsent(entity: OnlineTransactionJournalEntity): OnlineTransactionJournalEntity {
        val existing = get(
            tenantId = entity.tenantId,
            userId = entity.userId,
            outletId = entity.outletId,
            clientTransactionId = entity.clientTransactionId,
        )
        if (existing != null) return existing
        insert(entity)
        return entity
    }

    @Query(
        """
        SELECT * FROM online_transaction_journal
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND outletId = :outletId
          AND state IN ('PREPARED', 'SUBMITTING', 'UNCERTAIN', 'REQUIRES_RECONCILIATION')
        ORDER BY createdAt ASC
        LIMIT 1
        """,
    )
    suspend fun getUnresolved(
        tenantId: String,
        userId: String,
        outletId: String,
    ): OnlineTransactionJournalEntity?

    @Query(
        """
        SELECT * FROM online_transaction_journal
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND state IN ('PREPARED', 'SUBMITTING', 'UNCERTAIN', 'REQUIRES_RECONCILIATION')
        ORDER BY createdAt ASC
        LIMIT 1
        """,
    )
    suspend fun getUnresolvedForAccount(
        tenantId: String,
        userId: String,
    ): OnlineTransactionJournalEntity?

    @Query(
        """
        SELECT COUNT(*) FROM online_transaction_journal
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND state IN ('PREPARED', 'SUBMITTING', 'UNCERTAIN', 'REQUIRES_RECONCILIATION')
        """,
    )
    fun observeUnresolvedCount(tenantId: String, userId: String): Flow<Int>

    @Query(
        """
        UPDATE online_transaction_journal
        SET state = 'SUBMITTING',
            lastError = NULL,
            updatedAt = :updatedAt
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND outletId = :outletId
          AND clientTransactionId = :clientTransactionId
          AND state IN ('PREPARED', 'UNCERTAIN', 'REQUIRES_RECONCILIATION')
        """,
    )
    suspend fun markSubmitting(
        tenantId: String,
        userId: String,
        outletId: String,
        clientTransactionId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE online_transaction_journal
        SET state = 'UNCERTAIN',
            lastError = :error,
            updatedAt = :updatedAt
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND outletId = :outletId
          AND clientTransactionId = :clientTransactionId
          AND state = 'SUBMITTING'
        """,
    )
    suspend fun markUncertain(
        tenantId: String,
        userId: String,
        outletId: String,
        clientTransactionId: String,
        error: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE online_transaction_journal
        SET state = 'REQUIRES_RECONCILIATION',
            lastError = :error,
            updatedAt = :updatedAt
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND outletId = :outletId
          AND clientTransactionId = :clientTransactionId
          AND state IN ('PREPARED', 'SUBMITTING', 'UNCERTAIN', 'REQUIRES_RECONCILIATION')
        """,
    )
    suspend fun markRequiresReconciliation(
        tenantId: String,
        userId: String,
        outletId: String,
        clientTransactionId: String,
        error: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE online_transaction_journal
        SET state = 'REQUIRES_RECONCILIATION',
            lastError = :error,
            updatedAt = :updatedAt
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND state = 'SUBMITTING'
        """,
    )
    suspend fun recoverSubmittingForAccount(
        tenantId: String,
        userId: String,
        error: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE online_transaction_journal
        SET state = 'CONFIRMED',
            serverTransactionId = :serverTransactionId,
            lastError = NULL,
            updatedAt = :updatedAt
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND outletId = :outletId
          AND clientTransactionId = :clientTransactionId
          AND state = 'SUBMITTING'
        """,
    )
    suspend fun markConfirmed(
        tenantId: String,
        userId: String,
        outletId: String,
        clientTransactionId: String,
        serverTransactionId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE online_transaction_journal
        SET state = 'REJECTED',
            lastError = :error,
            updatedAt = :updatedAt
        WHERE tenantId = :tenantId
          AND userId = :userId
          AND outletId = :outletId
          AND clientTransactionId = :clientTransactionId
          AND state = 'SUBMITTING'
        """,
    )
    suspend fun markRejected(
        tenantId: String,
        userId: String,
        outletId: String,
        clientTransactionId: String,
        error: String,
        updatedAt: Long,
    ): Int
}
