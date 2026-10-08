package com.kasirkita.pos.presentation.product

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import com.kasirkita.pos.presentation.navigation.PosWorkspaceRailDestination
import com.kasirkita.pos.presentation.navigation.posWorkspaceShowsRail
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.presentation.cart.CartScreen
import com.kasirkita.pos.presentation.cart.CartViewModel
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTextField
import com.kasirkita.pos.ui.components.KasirTopBar
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
internal fun ProductScreen(
    onCartClick: () -> Unit,
    onHeldOrders: () -> Unit,
    railDestinations: List<PosWorkspaceRailDestination> = emptyList(),
    onRailDestinationClick: (PosWorkspaceRailDestination) -> Unit = {},
    viewModel: ProductCatalogViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cartState by cartViewModel.state.collectAsState()
    val modifierSelection by viewModel.modifierSelection.collectAsState()

    modifierSelection?.let { selection ->
        ModifierSelectionDialog(
            state = selection,
            onToggleOption = viewModel::toggleModifier,
            onNoteChange = viewModel::updateModifierNote,
            onConfirm = viewModel::confirmModifiers,
            onCancel = viewModel::cancelModifiers,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = posLayoutMode(maxWidth.value.toInt())
        Row(modifier = Modifier.fillMaxSize()) {
            if (posWorkspaceShowsRail(layoutMode)) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    railDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = destination.selected,
                            onClick = { onRailDestinationClick(destination) },
                            icon = { Text(destination.label.take(1)) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        when (layoutMode) {
            PosLayoutMode.Wide -> WideProductLayout(
                state = state,
                cart = cartState.cart,
                onRetry = viewModel::loadProducts,
                onRefresh = viewModel::refresh,
                onAddToCart = viewModel::addToCart,
                onCheckout = { onCartClick() },
                onHeldOrders = onHeldOrders,
                cartViewModel = cartViewModel,
            )
            PosLayoutMode.Narrow -> NarrowProductLayout(
                state = state,
                cart = cartState.cart,
                onRetry = viewModel::loadProducts,
                onRefresh = viewModel::refresh,
                onAddToCart = viewModel::addToCart,
                onCartClick = onCartClick,
            )
        }
            }
        }
    }
}

@Composable
private fun WideProductLayout(
    state: ProductCatalogState,
    cart: Cart,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onAddToCart: (ProductCatalogItem) -> Unit,
    onCheckout: () -> Unit,
    onHeldOrders: () -> Unit,
    cartViewModel: CartViewModel,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
        ) {
            when (state) {
                ProductCatalogState.Loading -> KasirLoadingState(message = "Memuat produk...")
                is ProductCatalogState.Error -> KasirErrorState(
                    message = state.message,
                    onRetry = onRetry,
                )
                is ProductCatalogState.Success -> ProductCatalogPanel(
                    items = state.items,
                    categories = state.categories,
                    message = state.message,
                    onRefresh = onRefresh,
                    onAddToCart = onAddToCart,
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            CartScreen(
                onCheckout = onCheckout,
                onHeldOrders = onHeldOrders,
                embedded = true,
                viewModel = cartViewModel,
            )
        }
    }
}

@Composable
private fun NarrowProductLayout(
    state: ProductCatalogState,
    cart: Cart,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onAddToCart: (ProductCatalogItem) -> Unit,
    onCartClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                ProductCatalogState.Loading -> KasirLoadingState(message = "Memuat produk...")
                is ProductCatalogState.Error -> KasirErrorState(
                    message = state.message,
                    onRetry = onRetry,
                )
                is ProductCatalogState.Success -> ProductCatalogPanel(
                    items = state.items,
                    categories = state.categories,
                    message = state.message,
                    onRefresh = onRefresh,
                    onAddToCart = onAddToCart,
                )
            }
        }
        CartSummaryBar(
            cart = cart,
            onCartClick = onCartClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProductCatalogPanel(
    items: List<ProductCatalogItem>,
    categories: List<com.kasirkita.pos.domain.model.Category>,
    message: String?,
    onRefresh: () -> Unit,
    onAddToCart: (ProductCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }

    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    val categoryFilters = remember(categories) {
        buildCategoryFilters(categories, items)
    }

    val filteredItems = remember(items, searchQuery, selectedCategoryId) {
        filterProductCatalog(items, searchQuery, selectedCategoryId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = KasirSpacing.CompactScreenPadding),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        KasirTopBar(title = "Kasir")

        KasirTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Cari produk atau SKU",
            modifier = Modifier.fillMaxWidth(),
        )

        if (categoryFilters.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
                contentPadding = PaddingValues(0.dp),
            ) {
                items(categoryFilters) { filter ->
                    val isSelected = (filter.id == null && selectedCategoryId == null) ||
                        (filter.id == selectedCategoryId)
                    KasirSecondaryButton(
                        text = filter.label,
                        onClick = { selectedCategoryId = filter.id },
                        enabled = true,
                        modifier = if (isSelected) Modifier else Modifier,
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
                text = "${filteredItems.size} produk",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KasirSecondaryButton(
                text = "Refresh",
                onClick = onRefresh,
            )
        }

        message?.let { feedback ->
            Text(
                text = feedback,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.supporting,
            )
        }

        if (filteredItems.isEmpty()) {
            KasirEmptyState(
                message = if (items.isEmpty()) "Belum ada produk" else "Tidak ada produk yang cocok",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
                horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
                contentPadding = PaddingValues(bottom = KasirSpacing.ItemGap),
            ) {
                items(
                    items = filteredItems,
                    key = { item -> item.product.id },
                ) { item ->
                    ProductCard(
                        item = item,
                        numberFormat = numberFormat,
                        onAddToCart = onAddToCart,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    item: ProductCatalogItem,
    numberFormat: NumberFormat,
    onAddToCart: (ProductCatalogItem) -> Unit,
) {
    val addEnabled = item.product.isActive && item.canAddToCart

    KasirCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.product.name,
            style = MaterialTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "SKU: ${item.product.sku}",
            style = MaterialTheme.typography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PriceText(
            text = "Rp${numberFormat.format(item.product.price)}",
            color = MaterialTheme.colorScheme.onSurface,
        )
        StockStatus(item = item)
        KasirPrimaryButton(
            text = "Tambah ke Keranjang",
            onClick = { onAddToCart(item) },
            modifier = Modifier.fillMaxWidth(),
            enabled = addEnabled,
        )
    }
}

@Composable
private fun StockStatus(item: ProductCatalogItem) {
    val badgeTone: StatusBadgeTone
    val badgeText: String
    val detailText: String

    when {
        !item.product.isActive -> {
            badgeTone = StatusBadgeTone.Error
            badgeText = "Tidak aktif"
            detailText = "Produk tidak aktif"
        }
        !item.product.trackStock -> {
            badgeTone = StatusBadgeTone.Neutral
            badgeText = "Tanpa pelacakan"
            detailText = "Stok tidak dikelola"
        }
        item.stockQuantity == null -> {
            badgeTone = StatusBadgeTone.Warning
            badgeText = "Stok belum tersedia"
            detailText = "Tetap dapat dijual offline"
        }
        item.stockQuantity <= 0 -> {
            badgeTone = StatusBadgeTone.Error
            badgeText = "Habis"
            detailText = "Stok: ${item.stockQuantity}"
        }
        else -> {
            badgeTone = StatusBadgeTone.Success
            badgeText = "Tersedia"
            detailText = "Stok: ${item.stockQuantity}"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Text(
            text = detailText,
            style = MaterialTheme.typography.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusBadge(
            text = badgeText,
            tone = badgeTone,
        )
    }
}

@Composable
private fun CartSummaryBar(
    cart: Cart,
    onCartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

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
                Column {
                    Text(
                        text = "Keranjang",
                        style = MaterialTheme.typography.supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${cart.totalItems()} item",
                        style = MaterialTheme.typography.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                PriceText(
                    text = "Rp${numberFormat.format(cart.totalAmount())}",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            KasirPrimaryButton(
                text = "Lihat Keranjang",
                onClick = onCartClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = cart.items.isNotEmpty(),
            )
        }
    }
}
