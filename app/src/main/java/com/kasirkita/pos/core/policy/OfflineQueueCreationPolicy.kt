package com.kasirkita.pos.core.policy

/**
 * Release policy for creating new local transaction queue records.
 * Existing records remain readable and recoverable when creation is disabled.
 */
fun interface OfflineQueueCreationPolicy {
    fun allowsNewRecords(): Boolean
}
