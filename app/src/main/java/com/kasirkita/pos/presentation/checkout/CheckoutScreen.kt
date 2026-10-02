package com.kasirkita.pos.presentation.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirTextField
import com.kasirkita.pos.ui.components.KasirTopBar
import com.kasirkita.pos.ui.components.PriceDisplay
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.supporting
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CheckoutScreen(
    onCheckoutSuccess: (String) -> Unit = {},
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }
    var paymentText by rememberSaveable { mutableStateOf("") }
    val transaction = state.transaction
    val queuedClientTransactionId = state.offlineQueuedClientTransactionId

    LaunchedEffect(transaction?.id) {
        transaction?.id?.let(onCheckoutSuccess)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = KasirSpacing.CompactScreenPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = KasirSpacing.ItemGap,
            ),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            item {
                KasirTopBar(title = "Pembayaran")
            }

            if (transaction != null) {
                item {
                    CheckoutSuccessContent(transaction = transaction, numberFormat = numberFormat)
                }
                return@LazyColumn
            }

            if (queuedClientTransactionId != null) {
                item {
                    CheckoutOfflineContent(
                        clientTransactionId = queuedClientTransactionId,
                        totalAmount = state.cart.totalAmount(),
                        numberFormat = numberFormat,
                    )
                }
                return@LazyColumn
            }

            item {
                OrderSummarySection(
                    itemCount = state.cart.totalItems(),
                    totalAmount = state.cart.totalAmount(),
                    numberFormat = numberFormat,
                )
            }

            items(
                items = state.cart.items,
                key = CartItem::productId,
            ) { item ->
                CheckoutItemRow(item = item, numberFormat = numberFormat)
            }

            item {
                PaymentMethodSection()
            }

            item {
                CashPaymentSection(
                    totalAmount = state.cart.totalAmount(),
                    paymentText = paymentText,
                    onPaymentChange = { value ->
                        paymentText = value.filter(Char::isDigit)
                    },
                    numberFormat = numberFormat,
                )
            }

            state.errorMessage?.let { message ->
                item {
                    KasirCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.supporting,
                        )
                    }
                }
            }
        }

        SubmitPaymentSection(
            isLoading = state.isLoading,
            isEnabled = !state.isLoading && state.cart.items.isNotEmpty(),
            totalAmount = state.cart.totalAmount(),
            paymentAmount = paymentText.toLongOrNull() ?: 0L,
            numberFormat = numberFormat,
            onSubmit = {
                viewModel.checkout(paymentText.toLongOrNull() ?: 0L)
            },
        )
    }
}

@Composable
private fun CheckoutSuccessContent(
    transaction: com.kasirkita.pos.domain.model.Transaction,
    numberFormat: NumberFormat,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Transaksi berhasil",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        PriceText(text = "Rp${numberFormat.format(transaction.total)}")
        transaction.change?.let { change ->
            Text(
                text = "Kembalian: Rp${numberFormat.format(change)}",
                style = MaterialTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "ID: ${transaction.id}",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CheckoutOfflineContent(
    clientTransactionId: String,
    totalAmount: Long,
    numberFormat: NumberFormat,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Transaksi tersimpan untuk sinkronisasi",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        PriceText(text = "Rp${numberFormat.format(totalAmount)}")
        Text(
            text = "Transaksi akan disinkronkan saat koneksi tersedia.",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrderSummarySection(
    itemCount: Int,
    totalAmount: Long,
    numberFormat: NumberFormat,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Total",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$itemCount item",
                style = MaterialTheme.typography.body,
            )
            PriceText(text = "Rp${numberFormat.format(totalAmount)}")
        }
    }
}

@Composable
private fun CheckoutItemRow(
    item: CartItem,
    numberFormat: NumberFormat,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Qty: ${item.quantity}",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Rp${numberFormat.format(item.subtotal())}",
            style = MaterialTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PaymentMethodSection() {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Metode pembayaran",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "CASH",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CashPaymentSection(
    totalAmount: Long,
    paymentText: String,
    onPaymentChange: (String) -> Unit,
    numberFormat: NumberFormat,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        KasirTextField(
            value = paymentText,
            onValueChange = onPaymentChange,
            label = "Uang diterima",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            prefix = "Rp",
            singleLine = true,
        )

        val paymentAmount = paymentText.toLongOrNull() ?: 0L
        val change = if (paymentAmount >= totalAmount) paymentAmount - totalAmount else 0L
        val isInsufficientCash = paymentAmount < totalAmount

        if (isInsufficientCash) {
            val shortfall = totalAmount - paymentAmount
            Text(
                text = "Kurang: Rp${numberFormat.format(shortfall)}",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            PriceDisplay(
                label = "Kembalian",
                value = "Rp${numberFormat.format(change)}",
            )
        }
    }
}

@Composable
private fun SubmitPaymentSection(
    isLoading: Boolean,
    isEnabled: Boolean,
    totalAmount: Long,
    paymentAmount: Long,
    numberFormat: NumberFormat,
    onSubmit: () -> Unit,
) {
    val canSubmit = isEnabled && paymentAmount >= totalAmount && !isLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KasirSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        if (paymentAmount < totalAmount) {
            Text(
                text = "Pembayaran belum cukup",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        KasirPrimaryButton(
            text = "Selesaikan Transaksi",
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
            isLoading = isLoading,
        )
    }
}
