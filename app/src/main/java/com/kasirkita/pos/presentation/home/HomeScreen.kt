package com.kasirkita.pos.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onProductsClick: () -> Unit,
    onCartClick: () -> Unit,
    onShiftClick: () -> Unit,
) {
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

        Button(
            onClick = onProductsClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Produk")
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
