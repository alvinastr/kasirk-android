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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
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
    val transaction = state.transaction
    val queuedClientTransactionId = state.offlineQueuedClientTransactionId

    LaunchedEffect(transaction?.id, state.printerWarning) {
        if (transaction != null && state.printerWarning == null) {
            onCheckoutSuccess(transaction.id)
        }
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
                    CheckoutSuccessContent(
                        transaction = transaction,
                        numberFormat = numberFormat,
                        printerWarning = state.printerWarning,
                        onViewReceipt = { onCheckoutSuccess(transaction.id) },
                    )
                }
                return@LazyColumn
            }

            if (queuedClientTransactionId != null) {
                item {
                    CheckoutOfflineContent(
                        clientTransactionId = queuedClientTransactionId,
                        totalAmount = state.persistedTotal,
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
                key = { item -> item.lineKey.value },
            ) { item ->
                CheckoutItemRow(item = item, numberFormat = numberFormat)
            }

            item {
                PaymentMethodSection(
                    method = state.payment.method,
                    onMethodSelected = viewModel::selectPaymentMethod,
                )
            }

            item {
                PaymentControls(
                    payment = state.payment,
                    numberFormat = numberFormat,
                    onExactCash = viewModel::selectExactCash,
                    onQuickTender = viewModel::selectQuickTender,
                    onManualCash = viewModel::enterManualCash,
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

            // M16G-S2: reconciliation banner. Payment editing stays visible but the
            // pending payload is frozen, so an explicit retry action is the only way
            // to settle an unresolved Held Order checkout.
            if (state.reconciliationNeeded) {
                item {
                    KasirCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.reconciliationMessage
                                ?: "Status pembayaran belum dapat dipastikan. Silakan lakukan konfirmasi ulang.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.supporting,
                        )
                    }
                }
            }
        }

        if (state.reconciliationNeeded) {
            KasirPrimaryButton(
                text = if (state.isLoading) "Mengonfirmasi..." else "Konfirmasi Ulang Pembayaran",
                onClick = viewModel::retryHeldOrderCheckout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KasirSpacing.CompactScreenPadding, vertical = KasirSpacing.ItemGap),
                enabled = !state.isLoading,
            )
            return@Column
        }

        SubmitPaymentSection(
            payment = state.payment,
            isLoading = state.isLoading,
            isEnabled = state.cart.items.isNotEmpty(),
            onSubmit = viewModel::confirmPayment,
        )
    }
}

@Composable
private fun CheckoutSuccessContent(
    transaction: com.kasirkita.pos.domain.model.Transaction,
    numberFormat: NumberFormat,
    printerWarning: String? = null,
    onViewReceipt: () -> Unit = {},
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
        printerWarning?.let {
            Text(
                text = "Transaksi berhasil, tetapi struk gagal dicetak.",
                style = MaterialTheme.typography.body,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "ID: ${transaction.id}",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (printerWarning != null) {
            KasirPrimaryButton(
                text = "Lihat Struk",
                onClick = onViewReceipt,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
private fun PaymentMethodSection(
    method: CheckoutPaymentMethod,
    onMethodSelected: (CheckoutPaymentMethod) -> Unit,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Metode pembayaran",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            KasirSecondaryButton(
                text = "CASH",
                onClick = { onMethodSelected(CheckoutPaymentMethod.CASH) },
                modifier = Modifier.weight(1f),
                enabled = method != CheckoutPaymentMethod.CASH,
            )
            KasirSecondaryButton(
                text = "QRIS",
                onClick = { onMethodSelected(CheckoutPaymentMethod.QRIS) },
                modifier = Modifier.weight(1f),
                enabled = method != CheckoutPaymentMethod.QRIS,
            )
        }
        Text(
            text = "Dipilih: ${method.name}",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaymentControls(
    payment: CheckoutPaymentState,
    numberFormat: NumberFormat,
    onExactCash: () -> Unit,
    onQuickTender: (Long) -> Unit,
    onManualCash: (String) -> Unit,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        PriceDisplay(
            label = "Total",
            value = "Rp${numberFormat.format(payment.totalAmount)}",
        )

        if (payment.method == CheckoutPaymentMethod.QRIS) {
            Text(
                text = "QRIS toko (QR fisik/statik). Pastikan kasir memverifikasi pembayaran melalui QRIS toko sebelum konfirmasi.",
                style = MaterialTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@KasirCard
        }

        Text(
            text = "Uang diterima",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            KasirSecondaryButton(
                text = "Uang Pas",
                onClick = onExactCash,
                modifier = Modifier.weight(1f),
            )
        }
        payment.quickTenderCandidates.forEach { tender ->
            KasirSecondaryButton(
                text = "Rp${numberFormat.format(tender)}",
                onClick = { onQuickTender(tender) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KasirSecondaryButton(
            text = "Lainnya",
            onClick = { onManualCash(payment.manualInput) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (payment.tenderMode == CashTenderMode.MANUAL) {
            KasirTextField(
                value = payment.manualInput,
                onValueChange = onManualCash,
                label = "Jumlah uang diterima",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                prefix = "Rp",
                singleLine = true,
            )
        }

        payment.amountReceived?.let { received ->
            PriceDisplay(
                label = "Uang diterima",
                value = "Rp${numberFormat.format(received)}",
            )
        }
        if (payment.shortageAmount > 0L) {
            Text(
                text = "Kurang: Rp${numberFormat.format(payment.shortageAmount)}",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (payment.amountReceived != null) {
            PriceDisplay(
                label = "Kembalian",
                value = "Rp${numberFormat.format(payment.changeAmount)}",
            )
        }
    }
}

@Composable
private fun SubmitPaymentSection(
    payment: CheckoutPaymentState,
    isLoading: Boolean,
    isEnabled: Boolean,
    onSubmit: () -> Unit,
) {
    val canSubmit = isEnabled && payment.canSubmit && !isLoading
    val buttonText = if (payment.method == CheckoutPaymentMethod.QRIS) {
        "Konfirmasi Pembayaran QRIS"
    } else {
        "Selesaikan Transaksi"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KasirSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        if (payment.method == CheckoutPaymentMethod.CASH && !payment.canSubmit) {
            Text(
                text = "Pembayaran belum cukup",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        KasirPrimaryButton(
            text = buttonText,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
            isLoading = isLoading,
        )
    }
}
