package com.kasirkita.pos.presentation.product

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Cart
import com.kasirkita.pos.presentation.cart.CartScreen
import com.kasirkita.pos.presentation.cart.CartViewModel
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.components.KasirTopBar
import com.kasirkita.pos.ui.components.PriceText
import com.kasirkita.pos.ui.theme.KasirSpacing
import com.kasirkita.pos.ui.theme.body
import com.kasirkita.pos.ui.theme.supporting
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun ProductScreen(
    onCartClick: () -> Unit,
    onHeldOrders: () -> Unit,
    onCheckout: () -> Unit = onCartClick,
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
        Box(modifier = Modifier.fillMaxSize()) {
        when (layoutMode) {
            PosLayoutMode.Wide -> WideProductLayout(
                state = state,
                cart = cartState.cart,
                onRetry = viewModel::loadProducts,
                onRefresh = viewModel::refresh,
                onAddToCart = viewModel::addToCart,
                onCheckout = onCheckout,
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
    Row(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
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
                    cart = cart,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
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
                    cart = cart,
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
    cart: Cart,
    message: String?,
    onRefresh: () -> Unit,
    onAddToCart: (ProductCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    val categoryFilters = remember(categories) {
        buildCategoryFilters(categories, items)
    }

    val filteredItems = remember(items, searchQuery, selectedCategoryId) {
        filterProductCatalog(items, searchQuery, selectedCategoryId)
    }
    val cartQuantities = remember(cart) { cartQuantitiesByProductId(cart) }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val selectedCategory = categoryFilters.firstOrNull { it.id == selectedCategoryId }
        ?: categoryFilters.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = KasirSpacing.CompactScreenPadding),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
    ) {
        KasirTopBar(
            title = if (isSearchExpanded) "" else "Kasir",
            actions = {
                if (isSearchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .widthIn(min = 180.dp, max = 260.dp)
                            .focusRequester(focusRequester),
                        label = { Text("Cari produk atau SKU") },
                        singleLine = true,
                        shape = RoundedCornerShape(KasirSpacing.CornerRadius),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Hapus pencarian",
                                    )
                                }
                            }
                        },
                    )
                    IconButton(
                        onClick = {
                            isSearchExpanded = false
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup pencarian",
                        )
                    }
                } else {
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Cari produk atau SKU",
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh produk",
                    )
                }
            },
        )

        if (!isSearchExpanded && searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pencarian: ${searchQuery.trim()}",
                    style = MaterialTheme.typography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searchQuery = "" }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Hapus pencarian",
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryDropdown(
                filters = categoryFilters,
                selectedFilter = selectedCategory,
                expanded = isCategoryMenuExpanded,
                onExpandedChange = { isCategoryMenuExpanded = it },
                onFilterSelected = { selectedCategoryId = it },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${filteredItems.size} produk",
                style = MaterialTheme.typography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                columns = GridCells.Adaptive(minSize = 160.dp),
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
                        cartQuantity = cartQuantities[item.product.id] ?: 0,
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
    cartQuantity: Int,
    numberFormat: NumberFormat,
    onAddToCart: (ProductCatalogItem) -> Unit,
) {
    val addEnabled = item.product.isActive && item.canAddToCart

    Card(
        onClick = { onAddToCart(item) },
        enabled = addEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(KasirSpacing.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(productPlaceholderColor(item.product.id)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = productInitials(item.product.name),
                style = MaterialTheme.typography.headlineMedium,
                color = productPlaceholderContentColor(item.product.id),
            )
            if (cartQuantity > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(KasirSpacing.Small),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = cartQuantity.toString(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KasirSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
        ) {
            Text(
                text = item.product.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Rp${numberFormat.format(item.product.price)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (!addEnabled) {
                Text(
                    text = productUnavailableLabel(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun productPlaceholderColor(productId: String): Color = when (
    (productId.hashCode() and Int.MAX_VALUE) % 4
) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer
    2 -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun productPlaceholderContentColor(productId: String): Color = when (
    (productId.hashCode() and Int.MAX_VALUE) % 4
) {
    0 -> MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.onSecondaryContainer
    2 -> MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun productInitials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString(separator = "") { it.first().uppercaseChar().toString() }
    .ifEmpty { "?" }

private fun productUnavailableLabel(item: ProductCatalogItem): String = when {
    !item.product.isActive -> "Tidak tersedia"
    item.product.trackStock && item.stockQuantity == 0 -> "Habis"
    else -> "Tidak tersedia"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    filters: List<CategoryFilter>,
    selectedFilter: CategoryFilter,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFilterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedFilter.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Kategori") },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(KasirSpacing.CornerRadius),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            filters.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label) },
                    onClick = {
                        onFilterSelected(filter.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
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
