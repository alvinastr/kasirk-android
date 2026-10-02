package com.kasirkita.pos.presentation.outlet

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasirkita.pos.domain.model.Outlet
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirPrimaryButton
import com.kasirkita.pos.ui.theme.KasirSpacing

@Composable
fun OutletScreen(
    onOutletSelected: (Outlet) -> Unit,
    viewModel: OutletViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OutletEvent.OutletPersisted -> onOutletSelected(event.outlet)
                is OutletEvent.OutletSelectionFailed -> Unit  // Error handled by ViewModel state
            }
        }
    }

    when (val currentState = state) {
        OutletState.Loading -> LoadingContent()
        is OutletState.Error -> ErrorContent(
            message = currentState.message,
            onRetry = viewModel::loadOutlets,
        )
        is OutletState.Success -> OutletList(
            outlets = currentState.outlets,
            onOutletSelected = viewModel::selectOutlet,
        )
    }
}

@Composable
private fun LoadingContent() {
    KasirLoadingState(
        message = "Memuat outlet...",
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    KasirErrorState(
        message = message,
        onRetry = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OutletList(
    outlets: List<Outlet>,
    onOutletSelected: (Outlet) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = KasirSpacing.ScreenPadding, vertical = 16.dp),
    ) {
        Text(
            text = "Pilih outlet",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (outlets.isEmpty()) {
            KasirEmptyState(
                message = "Belum ada outlet aktif.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
            ) {
                items(
                    items = outlets,
                    key = Outlet::id,
                ) { outlet ->
                    OutletCard(
                        outlet = outlet,
                        onClick = { onOutletSelected(outlet) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OutletCard(
    outlet: Outlet,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KasirCard(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = outlet.name,
                style = MaterialTheme.typography.titleLarge,
            )
            outlet.address?.takeIf(String::isNotBlank)?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            KasirPrimaryButton(
                text = "Pilih outlet",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
