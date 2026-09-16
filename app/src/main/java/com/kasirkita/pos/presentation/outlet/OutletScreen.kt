package com.kasirkita.pos.presentation.outlet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kasirkita.pos.domain.model.Outlet

@Composable
fun OutletScreen(
    onOutletSelected: (Outlet) -> Unit,
    viewModel: OutletViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val currentState = state) {
        OutletState.Loading -> LoadingContent()
        is OutletState.Error -> ErrorContent(
            message = currentState.message,
            onRetry = viewModel::loadOutlets,
        )
        is OutletState.Success -> OutletList(
            outlets = currentState.outlets,
            onOutletSelected = { outlet ->
                viewModel.selectOutlet(outlet)
                onOutletSelected(outlet)
            },
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Coba lagi")
        }
    }
}

@Composable
private fun OutletList(
    outlets: List<Outlet>,
    onOutletSelected: (Outlet) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Pilih Outlet",
            modifier = Modifier.padding(bottom = 12.dp),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (outlets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Belum ada outlet aktif")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = outlets,
                    key = Outlet::id,
                ) { outlet ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = outlet.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        outlet.address?.takeIf(String::isNotBlank)?.let { address ->
                            Text(address)
                        }
                        Button(
                            onClick = { onOutletSelected(outlet) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Pilih Outlet")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
