package com.kasirkita.pos.core.session

import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionBoundaryCleaner @Inject constructor(
    private val cartRepository: CartRepository,
    private val outletRepository: OutletRepository,
    private val shiftRepository: ShiftRepository,
) {
    suspend fun clear(tenantId: String? = null, userId: String? = null): Throwable? {
        var firstFailure: Throwable? = null

        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }

        attempt { cartRepository.clearCart() }
        // The Held Order identity is bound to the cashier session that created it,
        // so it must never survive a logout/tenant switch or checkout stays blocked.
        attempt { cartRepository.detachHeldOrderIdentity() }
        attempt { outletRepository.clearSelectedOutlet(tenantId, userId) }
        attempt { shiftRepository.clearCurrentShift(tenantId, userId) }

        return firstFailure
    }
}
