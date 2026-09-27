package com.kasirkita.pos.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.presentation.authv2.LogoutState
import com.kasirkita.pos.presentation.authv2.LogoutViewModel

@Composable
fun HomeScreen(
    onProductsClick: () -> Unit,
    onCartClick: () -> Unit,
    onShiftClick: () -> Unit,
    onManageProductsClick: (() -> Unit)? = null,
    onTransactionsClick: (() -> Unit)? = null,
    onReportsClick: (() -> Unit)? = null,
    onLogoutComplete: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    logoutViewModel: LogoutViewModel = hiltViewModel(),
) {
    val syncState by viewModel.syncState.collectAsState()
    val logoutState by logoutViewModel.state.collectAsState()

    LaunchedEffect(logoutState) {
        if (logoutState == LogoutState.LoggedOut) {
            logoutViewModel.acknowledgeLoggedOut()
            onLogoutComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(
            space = 16.dp,
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

        onTransactionsClick?.let { onClick ->
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Riwayat Transaksi")
            }
        }

        onReportsClick?.let { onClick ->
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Laporan")
            }
        }

        OutlinedButton(
            onClick = logoutViewModel::logout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            enabled = logoutState != LogoutState.Loading,
        ) {
            Text(
                if (logoutState == LogoutState.Loading) {
                    "Keluar..."
                } else {
                    "Keluar"
                },
            )
        }

        (logoutState as? LogoutState.Error)?.let { state ->
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
