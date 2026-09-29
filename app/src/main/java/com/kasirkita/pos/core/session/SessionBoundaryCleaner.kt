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
    fun clear(): Throwable? {
        var firstFailure: Throwable? = null

        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                if (firstFailure == null) firstFailure = error
            }
        }

        attempt(cartRepository::clearCart)
        attempt(outletRepository::clearSelectedOutlet)
        attempt(shiftRepository::clearCurrentShift)

        return firstFailure
    }
}
