package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptSettings
import com.kasirkita.pos.domain.repository.ReceiptSettingsRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Resolves effective settings for the immutable tenant/outlet identity on a receipt. */
class ResolveReceiptSettingsUseCase @Inject constructor(
    private val repository: ReceiptSettingsRepository,
) {
    data class Resolution(
        val settings: ReceiptSettings?,
        val usedLegacyFallback: Boolean,
    )

    suspend operator fun invoke(receipt: Receipt): Resolution {
        val tenantId = receipt.tenant.id
        val outletId = receipt.outlet.id
        if (tenantId.isBlank() || outletId.isBlank() || tenantId != tenantId.trim() || outletId != outletId.trim()) {
            return Resolution(settings = null, usedLegacyFallback = true)
        }

        return try {
            val settings = repository.refreshSettings(tenantId, outletId).getOrElse { error ->
                if (error is CancellationException) throw error
                return Resolution(settings = null, usedLegacyFallback = true)
            }
            if (settings.tenantId != tenantId || settings.outletId != outletId) {
                Resolution(settings = null, usedLegacyFallback = true)
            } else {
                Resolution(settings = settings, usedLegacyFallback = false)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Resolution(settings = null, usedLegacyFallback = true)
        }
    }
}
