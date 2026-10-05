package com.kasirkita.pos.presentation.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirTopBar
import com.kasirkita.pos.ui.components.PriceDisplay
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.components.StatusBadge
import com.kasirkita.pos.ui.components.StatusBadgeTone
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.sectionTitle
import com.kasirkita.pos.ui.theme.supporting
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CartScreen(
    onCheckout: () -> Unit = {},
    embedded: Boolean = false,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    CartContent(
        state = state,
        numberFormat = numberFormat,
        onDecreaseQuantity = viewModel::decreaseQuantity,
        onIncreaseQuantity = viewModel::increaseQuantity,
        onRemoveProduct = viewModel::removeProduct,
        onCheckout = onCheckout,
    )
}

@Composable
private fun CartContent(
    state: CartState,
    numberFormat: NumberFormat,
    onDecreaseQuantity: (String) -> Unit,
    onIncreaseQuantity: (String) -> Unit,
    onRemoveProduct: (String) -> Unit,
    onCheckout: () -> Unit,
) {
    val cart = state.cart

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = KasirSpacing.CompactScreenPadding),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            KasirTopBar(title = "Keranjang")

            Text(
                text = "${cart.totalItems()} item dalam pesanan",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (cart.items.isEmpty()) {
                KasirEmptyState(
                    message = "Keranjang masih kosong. Tambahkan produk dari layar Kasir.",
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = KasirSpacing.SectionGap),
                    verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
                ) {
                    items(
                        items = cart.items,
                        key = { item -> item.lineKey.value },
                    ) { item ->
                        CartItemCard(
                            item = item,
                            numberFormat = numberFormat,
                            onDecreaseQuantity = { onDecreaseQuantity(item.lineKey.value) },
                            onIncreaseQuantity = { onIncreaseQuantity(item.lineKey.value) },
                            onRemove = { onRemoveProduct(item.lineKey.value) },
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.supporting,
                )
            }
        }

        OrderSummary(
            cart = cart,
            numberFormat = numberFormat,
            onCheckout = onCheckout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    numberFormat: NumberFormat,
    onDecreaseQuantity: () -> Unit,
    onIncreaseQuantity: () -> Unit,
    onRemove: () -> Unit,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (item.sku.isNotBlank()) {
            Text(
                text = "SKU: ${item.sku}",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (item.modifierSelections.isNotEmpty()) {
            Text(
                text = item.modifierSelections.joinToString(" • ") { it.optionName },
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item.note?.let { note ->
            Text(
                text = "Catatan: $note",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = "Harga satuan: Rp${numberFormat.format(item.price)}",
            style = MaterialTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StockPresentation(item = item)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuantityStepper(
                quantity = item.quantity,
                canIncrease = item.canIncreaseQuantity(),
                onDecrease = onDecreaseQuantity,
                onIncrease = onIncreaseQuantity,
            )

            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = KasirSpacing.ButtonMinHeight),
            ) {
                Text(
                    text = "Hapus",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        PriceDisplay(
            label = "Subtotal",
            value = "Rp${numberFormat.format(item.subtotal())}",
        )
    }
}

@Composable
private fun QuantityStepper(
    quantity: Int,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuantityButton(
            label = "−",
            onClick = onDecrease,
        )
        Text(
            text = quantity.toString(),
            modifier = Modifier.sizeIn(minWidth = 32.dp),
            style = MaterialTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        QuantityButton(
            label = "+",
            onClick = onIncrease,
            enabled = canIncrease,
        )
    }
}

@Composable
private fun QuantityButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(
                minWidth = KasirSpacing.ButtonMinHeight,
                minHeight = KasirSpacing.ButtonMinHeight,
            ),
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun StockPresentation(item: CartItem) {
    val text: String
    val tone: StatusBadgeTone

    when {
        !item.trackStock -> {
            text = "Stok tidak dikelola"
            tone = StatusBadgeTone.Neutral
        }
        item.availableStock == null -> {
            text = "Stok belum tersedia"
            tone = StatusBadgeTone.Warning
        }
        else -> {
            text = "Stok tersedia: ${item.availableStock}"
            tone = if (item.quantity >= item.availableStock) {
                StatusBadgeTone.Warning
            } else {
                StatusBadgeTone.Success
            }
        }
    }

    StatusBadge(
        text = text,
        tone = tone,
    )
}

@Composable
private fun OrderSummary(
    cart: Cart,
    numberFormat: NumberFormat,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(KasirSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total ${cart.totalItems()} item",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Total pembayaran",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriceText(
                text = "Rp${numberFormat.format(cart.totalAmount())}",
                modifier = Modifier.align(Alignment.End),
                color = MaterialTheme.colorScheme.onSurface,
            )
            KasirPrimaryButton(
                text = "Lanjut ke Checkout",
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth(),
                enabled = cart.items.isNotEmpty(),
            )
        }
    }
}
