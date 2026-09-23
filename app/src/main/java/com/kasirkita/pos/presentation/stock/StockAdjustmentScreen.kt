package com.kasirkita.pos.presentation.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Product
import com.kasirkita.pos.domain.model.StockAdjustmentType
import com.kasirkita.pos.presentation.product.ProductState
import com.kasirkita.pos.presentation.product.ProductViewModel

@Composable
fun StockAdjustmentScreen(
    productId: String,
    onCancel: () -> Unit,
    onAdjustmentSaved: () -> Unit,
    productViewModel: ProductViewModel = hiltViewModel(),
    stockViewModel: StockAdjustmentViewModel = hiltViewModel(),
) {
    val productState by productViewModel.state.collectAsState()
    val stockState by stockViewModel.state.collectAsState()

    LaunchedEffect(productId) {
        stockViewModel.loadStocks()
    }

    LaunchedEffect(stockState.adjustmentSucceeded) {
        if (stockState.adjustmentSucceeded) {
            stockViewModel.consumeAdjustmentSuccess()
            onAdjustmentSaved()
        }
    }

    when (val currentProductState = productState) {
        ProductState.Loading -> StockLoadingContent("Memuat data produk...")
        is ProductState.Error -> StockErrorContent(
            message = "Data produk tidak dapat dimuat.",
            onRetry = productViewModel::refresh,
            onCancel = onCancel,
        )
        is ProductState.Success -> {
            val product = currentProductState.products.firstOrNull { product ->
                product.id == productId
            }
            when {
                product == null -> StockErrorContent(
                    message = "Produk tidak ditemukan dalam daftar saat ini.",
                    onRetry = productViewModel::refresh,
                    onCancel = onCancel,
                )
                !product.trackStock -> StockUnavailableContent(
                    onCancel = onCancel,
                )
                stockState.isLoadingStock -> StockLoadingContent("Memuat stok saat ini...")
                stockState.stockLoadError != null -> StockErrorContent(
                    message = stockState.stockLoadError.orEmpty(),
                    onRetry = { stockViewModel.loadStocks(force = true) },
                    onCancel = onCancel,
                )
                stockState.hasLoadedStocks -> StockAdjustmentFormContent(
                    product = product,
                    currentStock = stockState.currentStock(product.id),
                    isSubmitting = stockState.isSubmitting,
                    submitError = stockState.submitError,
                    onInputChanged = stockViewModel::clearSubmitError,
                    onSubmit = { adjustmentType, quantity, reason ->
                        stockViewModel.createAdjustment(
                            productId = product.id,
                            adjustmentType = adjustmentType,
                            quantity = quantity,
                            reason = reason,
                        )
                    },
                    onCancel = onCancel,
                )
                else -> StockLoadingContent("Memuat stok saat ini...")
            }
        }
    }
}

@Composable
internal fun StockAdjustmentFormContent(
    product: Product,
    currentStock: Int,
    isSubmitting: Boolean,
    submitError: String?,
    onInputChanged: () -> Unit,
    onSubmit: (StockAdjustmentType, Int, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var adjustmentType by rememberSaveable(product.id) {
        mutableStateOf(StockAdjustmentType.ADD)
    }
    var quantity by rememberSaveable(product.id) { mutableStateOf("") }
    var reason by rememberSaveable(product.id) { mutableStateOf("") }
    var quantityError by rememberSaveable(product.id) { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Penyesuaian Stok",
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = "Produk",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "SKU: ${product.sku}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Stok saat ini",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = currentStock.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "Jenis penyesuaian",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                AdjustmentTypeOption(
                    label = "(+) Tambah stok",
                    selected = adjustmentType == StockAdjustmentType.ADD,
                    enabled = !isSubmitting,
                    onClick = {
                        adjustmentType = StockAdjustmentType.ADD
                        onInputChanged()
                    },
                )
                AdjustmentTypeOption(
                    label = "(-) Kurangi stok",
                    selected = adjustmentType == StockAdjustmentType.DEDUCT,
                    enabled = !isSubmitting,
                    onClick = {
                        adjustmentType = StockAdjustmentType.DEDUCT
                        onInputChanged()
                    },
                )
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = {
                    quantity = it
                    quantityError = null
                    onInputChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                isError = quantityError != null,
                label = { Text("Jumlah") },
                supportingText = quantityError?.let { message ->
                    { Text(message) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            OutlinedTextField(
                value = reason,
                onValueChange = {
                    reason = it
                    onInputChanged()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                label = { Text("Alasan (opsional)") },
                minLines = 2,
                maxLines = 4,
            )

            if (submitError != null) {
                Text(
                    text = submitError,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    when (
                        val result = validateStockAdjustmentForm(
                            StockAdjustmentFormInput(
                                adjustmentType = adjustmentType,
                                quantity = quantity,
                                reason = reason,
                            ),
                        )
                    ) {
                        is StockAdjustmentFormResult.Invalid -> {
                            quantityError = result.quantityError
                        }
                        is StockAdjustmentFormResult.Valid -> onSubmit(
                            result.adjustmentType,
                            result.quantity,
                            result.reason,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = !isSubmitting,
            ) {
                if (isSubmitting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Text("Menyimpan stok...")
                    }
                } else {
                    Text("Simpan Penyesuaian")
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = !isSubmitting,
            ) {
                Text("Batal")
            }
        }
    }
}

@Composable
private fun AdjustmentTypeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun StockLoadingContent(message: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
internal fun StockErrorContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text("Coba lagi")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text("Kembali")
            }
        }
    }
}

@Composable
private fun StockUnavailableContent(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Produk ini tidak menggunakan pelacakan stok.")
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text("Kembali")
            }
        }
    }
}
