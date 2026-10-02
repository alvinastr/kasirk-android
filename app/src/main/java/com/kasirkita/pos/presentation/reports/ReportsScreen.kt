package com.kasirkita.pos.presentation.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kasirkita.pos.domain.model.DailySalesReport
import com.kasirkita.pos.domain.model.TopProductReport
import com.kasirkita.pos.ui.components.KasirErrorState
import com.kasirkita.pos.ui.components.KasirLoadingState
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.theme.KasirSpacing
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
                .padding(horizontal = KasirSpacing.Large),
        ) {
            ReportsHeader(onBack = onBack)

            when (state) {
                ReportsState.Loading -> ReportsCenteredContent {
                    KasirLoadingState(message = "Memuat laporan...")
                }

                is ReportsState.Error -> ReportsCenteredContent {
                    KasirErrorState(message = state.message, onRetry = onRetry)
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
            .padding(vertical = KasirSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KasirSpacing.Small),
    ) {
        KasirTextButton(
            text = "Kembali",
            onClick = onBack,
        )
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
            space = KasirSpacing.Large,
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
            bottom = KasirSpacing.XXLarge,
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
                    modifier = Modifier.padding(vertical = KasirSpacing.Large),
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
        modifier = Modifier.padding(
            top = KasirSpacing.XXLarge,
            bottom = KasirSpacing.Small,
        ),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReportMetricRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KasirSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
    ) {
        Text(
            text = label,
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
            .padding(vertical = KasirSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(KasirSpacing.XSmall),
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
