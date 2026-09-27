package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.ResolvedStore
import com.kasirkita.pos.domain.repository.AuthV2Repository
import javax.inject.Inject

class ResolveStoreUseCase @Inject constructor(
    private val authV2Repository: AuthV2Repository,
) {
    suspend operator fun invoke(storeCode: String): Result<ResolvedStore> =
        authV2Repository.resolveStore(storeCode)
}
