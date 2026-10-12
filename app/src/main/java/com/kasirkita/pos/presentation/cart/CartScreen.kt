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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kasirkita.pos.core.policy.HELD_ORDER_CHECKOUT_ENABLED
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.domain.model.CartItem
import com.kasirkita.pos.presentation.heldorder.heldOrderErrorMessage
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.supporting
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CartScreen(
    onCheckout: () -> Unit,
    onHeldOrders: () -> Unit,
    onAddProducts: () -> Unit = {},
    embedded: Boolean = false,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    LaunchedEffect(state.selectedOutlet?.id) {
        if (state.selectedOutlet != null && !state.heldOrdersLoaded) {
            viewModel.listHeldOrders()
        }
    }

    CartContent(
        state = state,
        numberFormat = numberFormat,
        onDecreaseQuantity = viewModel::decreaseQuantity,
        onIncreaseQuantity = viewModel::increaseQuantity,
        onRemoveProduct = viewModel::removeProduct,
        onCheckout = onCheckout,
        onHeldOrders = onHeldOrders,
        onAddProducts = onAddProducts,
        showAddProductsAction = !embedded,
        onSaveHeldOrder = viewModel::saveHeldOrder,
        onSaveHeldOrderChanges = viewModel::updateHeldOrder,
        onCancelHeldOrder = viewModel::cancelAttachedHeldOrder,
        onClearHeldOrderError = viewModel::clearHeldOrderError,
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
    onHeldOrders: () -> Unit,
    onAddProducts: () -> Unit,
    showAddProductsAction: Boolean,
    onSaveHeldOrder: (String?) -> Unit,
    onSaveHeldOrderChanges: (String?) -> Unit,
    onCancelHeldOrder: () -> Unit,
    onClearHeldOrderError: () -> Unit,
) {
    val cart = state.cart
    var showSaveDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var label by remember(state.heldOrderId, state.heldOrderLabel) {
        mutableStateOf(state.heldOrderLabel.orEmpty())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = KasirSpacing.CompactScreenPadding),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            CartHeader(itemCount = cart.totalItems())

            state.heldOrderId?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Edit: ${state.heldOrderLabel?.takeIf { label -> label.isNotBlank() } ?: "Order tersimpan"}",
                        style = MaterialTheme.typography.supporting,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    KasirSecondaryButton(
                        text = "Batalkan Edit",
                        onClick = onCancelHeldOrder,
                        enabled = state.heldOrderOperation != HeldOrderOperation.CANCEL,
                    )
                }
            }

            if (cart.items.isEmpty()) {
                CartEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = KasirSpacing.Small),
                ) {
                    itemsIndexed(
                        items = cart.items,
                        key = { _, item -> item.lineKey.value },
                    ) { index, item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CartItemRow(
                                item = item,
                                numberFormat = numberFormat,
                                onDecreaseQuantity = { onDecreaseQuantity(item.lineKey.value) },
                                onIncreaseQuantity = { onIncreaseQuantity(item.lineKey.value) },
                                onRemove = { onRemoveProduct(item.lineKey.value) },
                            )
                            if (index < cart.items.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
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
            state = state,
            numberFormat = numberFormat,
            onCheckout = onCheckout,
            onHeldOrders = onHeldOrders,
            onAddProducts = onAddProducts,
            showAddProductsAction = showAddProductsAction,
            onSaveClicked = { showSaveDialog = true },
            onCancelEditClicked = { showCancelDialog = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(if (state.heldOrderId != null) "Simpan perubahan" else "Simpan order") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nama order (opsional)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    if (state.heldOrderId != null) onSaveHeldOrderChanges(label) else onSaveHeldOrder(label)
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    onClearHeldOrderError()
                }) { Text("Batal") }
            },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Batalkan order tersimpan?") },
            text = {
                Text(
                    "Order akan dibatalkan di server dan cart saat ini tetap isi sebagai pesanan biasa.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    onCancelHeldOrder()
                }) { Text("Batalkan Order") }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("Kembali") } },
        )
    }
}

@Composable
private fun CartHeader(itemCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = KasirSpacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Keranjang",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$itemCount item",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CartEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.Small, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.ShoppingBasket,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Keranjang masih kosong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Pilih produk untuk mulai transaksi",
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    numberFormat: NumberFormat,
    onDecreaseQuantity: () -> Unit,
    onIncreaseQuantity: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.modifierSelections.isNotEmpty()) {
                    Text(
                        text = item.modifierSelections.joinToString(" • ") { selection ->
                            selection.groupName?.takeIf { it.isNotBlank() }?.let {
                                "$it: ${selection.optionName}"
                            } ?: selection.optionName
                        },
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
                    text = "Satuan Rp${numberFormat.format(item.price)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Text(
                text = "Rp${numberFormat.format(item.subtotal())}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

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
private fun OrderSummary(
    cart: Cart,
    state: CartState,
    numberFormat: NumberFormat,
    onCheckout: () -> Unit,
    onHeldOrders: () -> Unit,
    onAddProducts: () -> Unit,
    showAddProductsAction: Boolean,
    onSaveClicked: () -> Unit,
    onCancelEditClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
                Column(
                    modifier = Modifier.padding(KasirSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
                ) {
                    if (state.isEditingHeldOrder) {
                        if (showAddProductsAction) {
                            KasirSecondaryButton(
                                text = "Tambah Pesanan",
                                onClick = onAddProducts,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.heldOrderOperation == null,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
                        ) {
                            KasirPrimaryButton(
                                text = "Simpan Perubahan",
                                onClick = onSaveClicked,
                                modifier = Modifier.weight(1f),
                                enabled = state.heldOrderOperation == null,
                            )
                            KasirSecondaryButton(
                                text = "Batalkan Edit",
                                onClick = onCancelEditClicked,
                                modifier = Modifier.weight(1f),
                                enabled = state.heldOrderOperation != HeldOrderOperation.CANCEL,
                            )
                        }
                        state.heldOrderError?.let { error ->
                            Text(
                                text = heldOrderErrorMessage(error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.supporting,
                            )
                        }
                    } else {
                        if (HELD_ORDER_CHECKOUT_ENABLED) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
                            ) {
                                KasirSecondaryButton(
                                    text = "Simpan Order",
                                    onClick = onSaveClicked,
                                    modifier = Modifier.weight(1f),
                                    enabled = cart.items.isNotEmpty() && state.heldOrderOperation == null,
                                )
                                KasirSecondaryButton(
                                    text = when (val total = heldOrderBadgeCount(state.heldOrderTotalState)) {
                                        null -> "Order Tersimpan"
                                        else -> "Order Tersimpan ($total)"
                                    },
                                    onClick = onHeldOrders,
                                    modifier = Modifier.weight(1f),
                                    enabled = state.heldOrderOperation == null,
                                )
                            }
                        }
                    }
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
                        text = (if (state.isEditingHeldOrder) "Proses Bayar" else "Checkout") +
                            " · Rp${numberFormat.format(cart.totalAmount())}",
                        onClick = onCheckout,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cart.items.isNotEmpty() && state.heldOrderOperation == null,
                    )
                }
            }
}
