package com.kasirkita.pos.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.TopProductReport
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.usecase.GetDailySalesUseCase
import com.kasirkita.pos.domain.usecase.GetTopProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getDailySales: GetDailySalesUseCase,
    private val getTopProducts: GetTopProductsUseCase,
    private val outletRepository: OutletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ReportsState>(ReportsState.Loading)
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init {
        loadReports()
    }

    fun loadReports() {
        viewModelScope.launch {
            _state.value = ReportsState.Loading

            val (startDate, endDate) = reportsDateRange(LocalDate.now())
            val outletId = outletRepository.selectedOutlet.value?.id
            val dailyResult = getDailySales(
                date = startDate,
                outletId = outletId,
            )
            val topProductsResult = getTopProducts(
                startDate = startDate,
                endDate = endDate,
                limit = TOP_PRODUCTS_LIMIT,
                outletId = outletId,
            )

            _state.value = reportsState(
                dailyResult = dailyResult,
                topProductsResult = topProductsResult,
            )
        }
    }
}

internal fun reportsDateRange(today: LocalDate): Pair<String, String> =
    today.toString() to today.plusDays(1).toString()

internal fun reportsState(
    dailyResult: Result<DailySalesReport>,
    topProductsResult: Result<List<TopProductReport>>,
): ReportsState {
    val dailySales = dailyResult.getOrElse { throwable ->
        return ReportsState.Error(reportsErrorMessage(throwable))
    }
    val topProducts = topProductsResult.getOrElse { throwable ->
        return ReportsState.Error(reportsErrorMessage(throwable))
    }

    return if (topProducts.isEmpty()) {
        ReportsState.Empty(dailySales)
    } else {
        ReportsState.Success(
            dailySales = dailySales,
            topProducts = topProducts,
        )
    }
}

internal fun reportsErrorMessage(throwable: Throwable): String = when {
    throwable is IOException ->
        "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    throwable is HttpException && throwable.code() == 401 ->
        "Sesi sudah berakhir. Silakan login kembali."
    throwable is HttpException && throwable.code() == 403 ->
        "Anda tidak memiliki akses ke laporan."
    else -> "Laporan tidak dapat dimuat. Coba lagi."
}

private const val TOP_PRODUCTS_LIMIT = 10
