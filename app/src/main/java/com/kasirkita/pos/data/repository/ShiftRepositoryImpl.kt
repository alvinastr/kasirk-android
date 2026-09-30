package com.kasirkita.pos.data.repository

import com.kasirkita.pos.core.datastore.AuthSessionDataStore
import com.kasirkita.pos.core.datastore.OperationalContextDataStore
import com.kasirkita.pos.data.api.ShiftApi
import com.kasirkita.pos.data.model.CloseShiftRequest
import com.kasirkita.pos.data.model.OpenShiftRequest
import com.kasirkita.pos.data.model.ShiftResponse
import com.kasirkita.pos.data.model.toDomain
import com.kasirkita.pos.domain.model.Shift
import com.kasirkita.pos.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepositoryImpl @Inject constructor(
    private val shiftApi: ShiftApi,
    private val authSessionDataStore: AuthSessionDataStore,
    private val operationalContextDataStore: OperationalContextDataStore,
) : ShiftRepository {

    private val _currentShift = MutableStateFlow<Shift?>(null)
    override val currentShift: StateFlow<Shift?> = _currentShift.asStateFlow()

    override suspend fun getCurrentShift(): Result<Shift?> {
        val result = runCatching {
            val response = shiftApi.getCurrentShift()
            when {
                response.isSuccessful -> response.requireBody().toDomain()
                response.code() == HTTP_NOT_FOUND -> null
                else -> throw HttpException(response)
            }
        }

        result.onSuccess { shift ->
            _currentShift.value = shift
            persistShiftState(shift)
        }
        return result
    }

    override suspend fun openShift(
        outletId: String,
        openingCash: Long,
    ): Result<Shift> {
        val result = runCatching {
            val response = shiftApi.openShift(
                OpenShiftRequest(
                    outletId = outletId,
                    openingCash = openingCash,
                ),
            )
            if (!response.isSuccessful) throw HttpException(response)
            response.requireBody().toDomain()
        }

        result.onSuccess { shift ->
            _currentShift.value = shift
            persistShiftState(shift)
        }
        return result
    }

    override suspend fun closeShift(
        shiftId: String,
        closingCash: Long,
    ): Result<Shift> {
        val result = runCatching {
            val response = shiftApi.closeShift(
                shiftId = shiftId,
                request = CloseShiftRequest(closingCash = closingCash),
            )
            if (!response.isSuccessful) throw HttpException(response)
            response.requireBody().toDomain()
        }

        result.onSuccess {
            _currentShift.value = null
            persistShiftState(null)
        }
        return result
    }

    override suspend fun clearCurrentShift() {
        _currentShift.value = null
        persistShiftState(null)
    }

    override suspend fun restoreCurrentShift() {
        val session = authSessionDataStore.getSession() ?: return
        val restored = operationalContextDataStore.getShift(session.tenantId, session.userId)
        if (restored != null && restored.status.equals("OPEN", ignoreCase = true)) {
            _currentShift.value = restored
        }
    }

    private suspend fun persistShiftState(shift: Shift?) {
        val session = authSessionDataStore.getSession() ?: return
        if (shift != null) {
            operationalContextDataStore.saveShift(session.tenantId, session.userId, shift)
        } else {
            operationalContextDataStore.clearShift(session.tenantId, session.userId)
        }
    }

    private fun Response<ShiftResponse>.requireBody(): ShiftResponse =
        body() ?: error("Shift response body is empty")

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
