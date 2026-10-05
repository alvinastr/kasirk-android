package com.kasirkita.pos.presentation.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Receipt
import com.kasirkita.pos.domain.model.ReceiptItem
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.PriceDisplay
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.sectionTitle
import com.kasirkita.pos.ui.theme.supporting
import java.text.NumberFormat
import java.util.Locale
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReceiptScreen(
    onNewTransaction: () -> Unit = {},
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val currentState = state) {
        ReceiptState.Loading -> KasirLoadingState(message = "Memuat struk...")

        is ReceiptState.Error -> ReceiptErrorContent(
            message = currentState.message,
            onRetry = viewModel::loadReceipt,
            onNewTransaction = onNewTransaction,
        )

        is ReceiptState.Success -> ReceiptContent(
            receipt = currentState.receipt,
            onNewTransaction = onNewTransaction,
        )
    }
}

@Composable
private fun ReceiptErrorContent(
    message: String,
    onRetry: () -> Unit,
    onNewTransaction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KasirSpacing.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = KasirSpacing.ItemGap,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        KasirCard {
            androidx.compose.material3.Text(
                text = message,
                style = MaterialTheme.typography.body,
            )
        }
        KasirSecondaryButton(
            text = "Coba Lagi",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        KasirPrimaryButton(
            text = "Transaksi Baru",
            onClick = onNewTransaction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReceiptContent(
    receipt: Receipt,
    onNewTransaction: () -> Unit,
) {
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
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
                ReceiptHeader(receipt = receipt)
            }

            item {
                ReceiptItemsSection(items = receipt.items, numberFormat = numberFormat)
            }

            item {
                ReceiptTotalsSection(receipt = receipt, numberFormat = numberFormat)
            }

            item {
                ReceiptPaymentSection(receipt = receipt, numberFormat = numberFormat)
            }

            item {
                ReceiptFooter(transactionId = receipt.transactionId)
            }
        }

        KasirPrimaryButton(
            text = "Transaksi Baru",
            onClick = onNewTransaction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(KasirSpacing.Large),
        )
    }
}

@Composable
private fun ReceiptHeader(receipt: Receipt) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern(
            "dd MMM yyyy, HH.mm",
            java.util.Locale.forLanguageTag("id-ID"),
        )
    }
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            text = "Transaksi Berhasil",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        androidx.compose.material3.Text(
            text = receipt.tenant.name,
            style = MaterialTheme.typography.sectionTitle,
        )
        receipt.outlet.address?.let { address ->
            androidx.compose.material3.Text(
                text = address,
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Text(
            text = "Outlet: ${receipt.outlet.name}",
            style = MaterialTheme.typography.body,
        )
        androidx.compose.material3.Text(
            text = formatTransactionDate(receipt.createdAt, dateFormatter),
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Text(
            text = "ID: ${receipt.transactionId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Text(
            text = "Kasir: ${receipt.cashier.name}",
            style = MaterialTheme.typography.body,
        )
        receipt.customer?.let { customer ->
            androidx.compose.material3.Text(
                text = "Customer: ${customer.name}",
                style = MaterialTheme.typography.body,
            )
        }
    }
}

private fun formatTransactionDate(
    value: String,
    formatter: DateTimeFormatter,
): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(formatter)
}.getOrDefault(value)

@Composable
private fun ReceiptItemsSection(
    items: List<ReceiptItem>,
    numberFormat: NumberFormat,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            text = "Item",
            style = MaterialTheme.typography.sectionTitle,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = KasirSpacing.Small))

        items.forEach { item ->
            ReceiptItemRow(item = item, numberFormat = numberFormat)
        }
    }
}

@Composable
private fun ReceiptItemRow(
    item: ReceiptItem,
    numberFormat: NumberFormat,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            androidx.compose.material3.Text(
                text = item.productName,
                style = MaterialTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.sku.isNotBlank()) {
                androidx.compose.material3.Text(
                    text = "SKU: ${item.sku}",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item.modifierSnapshots.forEach { modifier ->
                androidx.compose.material3.Text(
                    text = "- ${modifier.groupName}: ${modifier.optionName} (+Rp${numberFormat.format(modifier.priceDelta)})",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item.note?.takeIf { it.isNotBlank() }?.let { note ->
                androidx.compose.material3.Text(
                    text = "Note: $note",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            androidx.compose.material3.Text(
                text = "${item.quantity} × Rp${numberFormat.format(item.unitPrice)}",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Text(
                text = "Rp${numberFormat.format(item.subtotal)}",
                style = MaterialTheme.typography.body,
            )
        }
    }
}

@Composable
private fun ReceiptTotalsSection(
    receipt: Receipt,
    numberFormat: NumberFormat,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        PriceDisplay(
            label = "Subtotal",
            value = "Rp${numberFormat.format(receipt.subtotal)}",
        )
        if (receipt.discount > 0L) {
            PriceDisplay(
                label = "Diskon",
                value = "Rp${numberFormat.format(receipt.discount)}",
            )
        }
        if (receipt.tax > 0L) {
            PriceDisplay(
                label = "Pajak",
                value = "Rp${numberFormat.format(receipt.tax)}",
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = KasirSpacing.Small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text(
                text = "Total",
                style = MaterialTheme.typography.sectionTitle,
            )
            PriceText(text = "Rp${numberFormat.format(receipt.total)}")
        }
    }
}

@Composable
private fun ReceiptPaymentSection(
    receipt: Receipt,
    numberFormat: NumberFormat,
) {
    val payment = receipt.payment
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            text = "Metode pembayaran",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Text(
            text = payment?.method ?: "-",
            style = MaterialTheme.typography.titleMedium,
        )

        val showCashTender = payment?.method?.equals("CASH", ignoreCase = true) == true
            && payment.amountReceived != null

        val showCashChange = payment?.method?.equals("CASH", ignoreCase = true) == true
            && payment.changeAmount != null

        PriceDisplay(
            label = "Total",
            value = "Rp${numberFormat.format(payment?.amount ?: 0L)}",
        )

        if (showCashTender) {
            PriceDisplay(
                label = "Uang diterima",
                value = "Rp${numberFormat.format(payment.amountReceived)}",
            )
        }

        if (showCashChange) {
            HorizontalDivider(modifier = Modifier.padding(vertical = KasirSpacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    text = "Kembalian",
                    style = MaterialTheme.typography.sectionTitle,
                )
                PriceText(text = "Rp${numberFormat.format(payment.changeAmount)}")
            }
        }
    }
}

@Composable
private fun ReceiptFooter(transactionId: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        androidx.compose.material3.Text(
            text = "ID Transaksi",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Text(
            text = transactionId,
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
