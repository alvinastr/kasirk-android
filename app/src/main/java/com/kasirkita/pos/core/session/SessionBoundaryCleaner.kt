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
    suspend fun clear(): Throwable? {
        var firstFailure: Throwable? = null

        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }

        attempt { cartRepository.clearCart() }
        attempt { outletRepository.clearSelectedOutlet() }
        attempt { shiftRepository.clearCurrentShift() }

        return firstFailure
    }
}
