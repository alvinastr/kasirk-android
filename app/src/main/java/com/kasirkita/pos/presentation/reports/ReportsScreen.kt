package com.kasirkita.pos.presentation.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.TopProductReport
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ReportsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::loadReports,
    )
}

@Composable
internal fun ReportsContent(
    state: ReportsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            ReportsHeader(onBack = onBack)

            when (state) {
                ReportsState.Loading -> ReportsCenteredContent {
                    CircularProgressIndicator()
                    Text("Memuat laporan...")
                }

                is ReportsState.Error -> ReportsCenteredContent {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Coba Lagi")
                    }
                }

                is ReportsState.Empty -> ReportsBody(
                    dailySales = state.dailySales,
                    topProducts = emptyList(),
                )

                is ReportsState.Success -> ReportsBody(
                    dailySales = state.dailySales,
                    topProducts = state.topProducts,
                )
            }
        }
    }
}

@Composable
private fun ReportsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Kembali")
        }
        Text(
            text = "Laporan",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun ColumnScope.ReportsCenteredContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 16.dp,
            alignment = Alignment.CenterVertically,
        ),
        content = content,
    )
}

@Composable
private fun ColumnScope.ReportsBody(
    dailySales: DailySalesReport,
    topProducts: List<TopProductReport>,
) {
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = 24.dp,
        ),
    ) {
        item {
            ReportSectionTitle("Laporan Hari Ini")
            ReportMetricRow(
                label = "Total Penjualan",
                value = formatRupiah(dailySales.totalSales, numberFormat),
            )
            HorizontalDivider()
            ReportMetricRow(
                label = "Jumlah Transaksi",
                value = numberFormat.format(dailySales.transactionCount),
            )
            HorizontalDivider()
            ReportMetricRow(
                label = "Pajak",
                value = formatRupiah(dailySales.taxCollected, numberFormat),
            )
            HorizontalDivider()
            ReportMetricRow(
                label = "Keuntungan Kotor",
                value = formatRupiah(dailySales.grossProfit, numberFormat),
            )
        }

        item {
            ReportSectionTitle("Produk Terlaris")
        }

        if (topProducts.isEmpty()) {
            item {
                Text(
                    text = "Belum ada penjualan produk hari ini.",
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = topProducts,
                key = TopProductReport::productId,
            ) { product ->
                TopProductRow(
                    product = product,
                    numberFormat = numberFormat,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ReportSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReportMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TopProductRow(
    product: TopProductReport,
    numberFormat: NumberFormat,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = product.productName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "SKU: ${product.sku}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Terjual: ${numberFormat.format(product.quantitySold)}")
        Text(
            text = "Keuntungan kotor: ${formatRupiah(product.grossProfit, numberFormat)}",
        )
    }
}

private fun formatRupiah(
    amount: Long,
    numberFormat: NumberFormat,
): String = "Rp${numberFormat.format(amount)}"
