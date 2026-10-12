package com.kasirkita.pos.core.policy

/**
 * Release policy for the legacy offline queue. HF2 preserves rows but does not
 * allow background or manual submission of those historical records.
 */
fun interface LegacyOfflineQueuePolicy {
    fun allowsSynchronization(): Boolean
}
