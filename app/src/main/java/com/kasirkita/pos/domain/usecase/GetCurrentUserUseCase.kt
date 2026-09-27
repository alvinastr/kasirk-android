package com.kasirkita.pos.domain.usecase

import com.kasirkita.pos.domain.model.CurrentUser
import com.kasirkita.pos.domain.repository.AuthV2Repository
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authV2Repository: AuthV2Repository,
) {
    suspend operator fun invoke(): Result<CurrentUser> = authV2Repository.getCurrentUser()
}
