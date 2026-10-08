package com.kasirkita.pos.presentation.heldorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.HeldOrder
import com.kasirkita.pos.presentation.cart.CartViewModel
import com.kasirkita.pos.presentation.cart.HeldOrderOperation
import com.kasirkita.pos.presentation.cart.HeldOrderUiError
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTopBar
import com.kasirkita.pos.ui.components.PriceDisplay
import com.kasirkita.pos.ui.components.StatusBadge
import com.kasirkita.pos.ui.components.StatusBadgeTone
import com.kasirkita.pos.ui.theme.KasirSpacing

@Composable
fun HeldOrdersScreen(
    onBack: () -> Unit,
    onOpened: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var cancelCandidate by remember { mutableStateOf<HeldOrder?>(null) }
    var openCandidate by remember { mutableStateOf<HeldOrder?>(null) }
    var discardConfirmed by remember { mutableStateOf(false) }
    var pendingOpenId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.listHeldOrders() }
    // Navigate back to Cart only once the target order is actually attached to the
    // cart. A failed restore leaves the cashier on this screen with an error and
    // never pretends the order was opened.
    LaunchedEffect(state.heldOrderId, state.isEditingHeldOrder) {
        val target = pendingOpenId
        if (target != null && state.isEditingHeldOrder && state.heldOrderId == target) {
            pendingOpenId = null
            openCandidate = null
            discardConfirmed = false
            onOpened()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        KasirTopBar(
            title = "Order Tersimpan",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            },
        )
        when {
            state.heldOrderOperation == HeldOrderOperation.LIST && state.heldOrders.isEmpty() ->
                KasirLoadingState(message = "Memuat order tersimpan…", modifier = Modifier.fillMaxSize())

            state.heldOrderError != null && state.heldOrders.isEmpty() ->
                KasirErrorState(
                    message = heldOrderErrorMessage(state.heldOrderError),
                    onRetry = viewModel::listHeldOrders,
                    modifier = Modifier.fillMaxSize(),
                )

            state.heldOrders.isEmpty() ->
                KasirEmptyState(
                    message = "Belum ada order tersimpan yang masih terbuka.",
                    modifier = Modifier.fillMaxSize(),
                )

            else -> LazyColumn(
                contentPadding = PaddingValues(KasirSpacing.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
            ) {
                items(state.heldOrders, key = { it.id }) { order ->
                    HeldOrderRow(
                        order = order,
                        busy = state.heldOrderOperation != null,
                        onOpen = {
                            if (state.cart.items.isEmpty()) {
                                pendingOpenId = order.id
                                viewModel.restoreHeldOrder(order.id)
                            } else {
                                openCandidate = order
                            }
                        },
                        onCancel = { cancelCandidate = order },
                    )
                }
            }
        }
    }

    cancelCandidate?.let { order ->
        AlertDialog(
            onDismissRequest = { cancelCandidate = null },
            title = { Text("Batalkan order tersimpan?") },
            text = { Text("Order ini tidak dapat dibuka lagi setelah dibatalkan. Cart saat ini tidak akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    cancelCandidate = null
                    viewModel.cancelHeldOrder(order.id, order.version)
                }) { Text("Batalkan Order") }
            },
            dismissButton = { TextButton(onClick = { cancelCandidate = null }) { Text("Kembali") } },
        )
    }

    openCandidate?.takeIf { state.cart.items.isNotEmpty() && !discardConfirmed }?.let { order ->
        AlertDialog(
            onDismissRequest = { openCandidate = null },
            title = { Text("Cart saat ini belum kosong") },
            text = { Text("Simpan cart saat ini terlebih dahulu, buang cart lalu buka order, atau batalkan.") },
            confirmButton = {
                                TextButton(onClick = {
                                    pendingOpenId = order.id
                                    viewModel.saveCurrentCartThenRestoreHeldOrder(order.id, label = null)
                                }) { Text("Simpan Cart Dulu") }
                            },
            dismissButton = {
                Row {
                    TextButton(onClick = { openCandidate = null }) { Text("Batal") }
                    TextButton(onClick = { discardConfirmed = true }) { Text("Buang Cart") }
                }
            },
        )
    }

    openCandidate?.takeIf { discardConfirmed }?.let { order ->
        AlertDialog(
            onDismissRequest = { discardConfirmed = false },
            title = { Text("Buang cart saat ini?") },
            text = { Text("Tindakan ini mengganti seluruh isi cart dengan order tersimpan.") },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirmed = false
                    pendingOpenId = order.id
                    viewModel.restoreHeldOrder(order.id)
                }) { Text("Ya, Buang dan Buka") }
            },
            dismissButton = { TextButton(onClick = { discardConfirmed = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun HeldOrderRow(
    order: HeldOrder,
    busy: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(order.label?.takeIf { it.isNotBlank() } ?: "Order tanpa nama", style = MaterialTheme.typography.titleMedium)
        StatusBadge(text = order.status, tone = StatusBadgeTone.Success)
        Text("${order.itemCount} item • ${order.createdAt}", style = MaterialTheme.typography.bodySmall)
        order.cashierName?.let { Text("Kasir: $it", style = MaterialTheme.typography.bodySmall) }
        PriceDisplay(label = "Estimasi total", value = "Rp${order.totalEstimate}")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
        ) {
            KasirSecondaryButton(text = "Batalkan", onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f))
            KasirPrimaryButton(text = "Buka", onClick = onOpen, enabled = !busy, modifier = Modifier.weight(1f))
        }
    }
}

internal fun heldOrderErrorMessage(error: HeldOrderUiError?): String = when (error) {
    HeldOrderUiError.NO_OUTLET -> "Pilih outlet terlebih dahulu."
    HeldOrderUiError.NO_OPEN_SHIFT -> "Buka shift sebelum mengelola order tersimpan."
    HeldOrderUiError.SHIFT_OUTLET_MISMATCH -> "Shift aktif tidak sesuai dengan outlet."
    HeldOrderUiError.EMPTY_CART -> "Cart masih kosong."
    HeldOrderUiError.CART_CHANGED -> "Cart berubah saat disimpan. Order tersimpan sudah dibuat, cart tidak dikosongkan."
    HeldOrderUiError.CATALOG_CONFLICT -> "Order berisi produk atau modifier yang tidak lagi tersedia."
    HeldOrderUiError.STOCK_CONFLICT -> "Stok saat ini tidak cukup untuk memulihkan order ini."
    HeldOrderUiError.VERSION_CONFLICT -> "Order berubah di perangkat lain. Muat ulang sebelum menyimpan."
    HeldOrderUiError.NOT_OPEN -> "Order ini tidak lagi terbuka."
    HeldOrderUiError.NOT_FOUND -> "Order tidak ditemukan."
    HeldOrderUiError.RESOURCE_CONFLICT -> "Sesi kasir tidak valid untuk order ini."
    HeldOrderUiError.AUTHENTICATION_REQUIRED -> "Sesi login berakhir. Silakan masuk kembali."
    HeldOrderUiError.ACCESS_DENIED -> "Anda tidak memiliki akses ke order tersimpan untuk outlet ini."
    HeldOrderUiError.NETWORK -> "Order tersimpan memerlukan koneksi. Coba lagi."
    HeldOrderUiError.UNKNOWN -> "Order tersimpan gagal diproses. Coba lagi."
    null -> ""
}
