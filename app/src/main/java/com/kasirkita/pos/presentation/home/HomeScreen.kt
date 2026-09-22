package com.kasirkita.pos.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    onProductsClick: () -> Unit,
    onCartClick: () -> Unit,
    onShiftClick: () -> Unit,
    onManageProductsClick: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val syncState by viewModel.syncState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(
            space = 16.dp,
            alignment = Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "KasirKita POS",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Pending Sync: ${syncState.pendingCount}",
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            onClick = viewModel::syncNow,
            modifier = Modifier.fillMaxWidth(),
            enabled = syncState.pendingCount > 0 && !syncState.isSyncing,
        ) {
            if (syncState.isSyncing) {
                CircularProgressIndicator()
            } else {
                Text("Sync Sekarang")
            }
        }

        syncState.lastResult?.let { result ->
            Text(
                "Sync: ${result.synced} berhasil, " +
                    "${result.failed} gagal, ${result.pending} masih pending",
            )
        }

        syncState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onProductsClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Produk")
        }

        onManageProductsClick?.let { onClick ->
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Kelola Produk")
            }
        }

        Button(
            onClick = onCartClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cart")
        }

        Button(
            onClick = onShiftClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Shift")
        }
    }
}
