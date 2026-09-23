package com.kasirkita.pos.presentation.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasirkita.pos.domain.model.Stock
import com.kasirkita.pos.domain.model.StockAdjustmentType
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.usecase.CreateStockAdjustmentUseCase
import com.kasirkita.pos.domain.usecase.GetStocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StockAdjustmentViewModel @Inject constructor(
    private val getStocksUseCase: GetStocksUseCase,
    private val createStockAdjustmentUseCase: CreateStockAdjustmentUseCase,
    private val outletRepository: OutletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StockAdjustmentState())
    val state: StateFlow<StockAdjustmentState> = _state.asStateFlow()

    fun loadStocks(force: Boolean = false) {
        val currentState = _state.value
        if (currentState.isLoadingStock) return
        if (currentState.hasLoadedStocks && !force) return

        val outlet = outletRepository.selectedOutlet.value
        if (outlet == null) {
            _state.value = currentState.copy(
                hasLoadedStocks = false,
                stockLoadError = "Outlet belum dipilih. Kembali dan pilih outlet terlebih dahulu.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoadingStock = true,
                hasLoadedStocks = false,
                stockLoadError = null,
            )
            _state.value = stockStateAfterLoad(
                currentState = _state.value,
                result = getStocksUseCase(outlet.id),
            )
        }
    }

    fun createAdjustment(
        productId: String,
        adjustmentType: StockAdjustmentType,
        quantity: Int,
        reason: String?,
    ) {
        if (_state.value.isSubmitting) return

        val outlet = outletRepository.selectedOutlet.value
        if (outlet == null) {
            _state.value = _state.value.copy(
                submitError = "Outlet belum dipilih. Kembali dan pilih outlet terlebih dahulu.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isSubmitting = true,
                submitError = null,
                adjustmentSucceeded = false,
            )
            _state.value = stockStateAfterAdjustment(
                currentState = _state.value,
                result = createStockAdjustmentUseCase(
                    outletId = outlet.id,
                    productId = productId,
                    adjustmentType = adjustmentType,
                    quantity = quantity,
                    reason = reason,
                ),
            )
        }
    }

    fun clearSubmitError() {
        if (_state.value.submitError != null) {
            _state.value = _state.value.copy(submitError = null)
        }
    }

    fun consumeAdjustmentSuccess() {
        _state.value = _state.value.copy(adjustmentSucceeded = false)
    }
}

internal fun stockStateAfterLoad(
    currentState: StockAdjustmentState,
    result: Result<List<Stock>>,
): StockAdjustmentState = result.fold(
    onSuccess = { stocks ->
        currentState.copy(
            stocks = stocks,
            isLoadingStock = false,
            hasLoadedStocks = true,
            stockLoadError = null,
        )
    },
    onFailure = { throwable ->
        currentState.copy(
            isLoadingStock = false,
            hasLoadedStocks = false,
            stockLoadError = stockAdjustmentErrorMessage(throwable),
        )
    },
)

internal fun stockStateAfterAdjustment(
    currentState: StockAdjustmentState,
    result: Result<Stock>,
): StockAdjustmentState = result.fold(
    onSuccess = { updatedStock ->
        currentState.copy(
            stocks = currentState.stocks
                .filterNot { stock -> stock.productId == updatedStock.productId } +
                updatedStock,
            isSubmitting = false,
            submitError = null,
            adjustmentSucceeded = true,
        )
    },
    onFailure = { throwable ->
        currentState.copy(
            isSubmitting = false,
            submitError = stockAdjustmentErrorMessage(throwable),
            adjustmentSucceeded = false,
        )
    },
)

internal fun stockAdjustmentErrorMessage(throwable: Throwable): String {
    if (throwable is HttpException) {
        val responseBody = runCatching {
            throwable.response()?.errorBody()?.string()
        }.getOrNull().orEmpty().uppercase(Locale.ROOT)

        return when {
            "STOCK_TRACKING_DISABLED" in responseBody ->
                "Stok produk ini tidak dikelola."
            "PRODUCT_NOT_FOUND" in responseBody || "PRODUCT NOT FOUND" in responseBody ->
                "Produk tidak ditemukan. Daftar produk mungkin sudah berubah."
            "INSUFFICIENT_STOCK" in responseBody || "INSUFFICIENT STOCK" in responseBody ->
                "Stok tidak mencukupi untuk dikurangi sebanyak itu."
            throwable.code() == 401 ->
                "Sesi login berakhir. Silakan login kembali."
            throwable.code() == 403 ->
                "Anda tidak memiliki izin untuk menyesuaikan stok."
            else -> "Stok tidak dapat diperbarui. Coba lagi."
        }
    }

    return if (throwable is IOException) {
        "Tidak dapat terhubung ke server. Periksa koneksi lalu coba lagi."
    } else {
        "Stok tidak dapat diperbarui. Coba lagi."
    }
}
