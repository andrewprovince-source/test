package com.driveforchange.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pointerInput
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driveforchange.app.viewmodel.StatsGranularity
import com.driveforchange.app.viewmodel.StatsMetric
import com.driveforchange.app.viewmodel.StatsPeriod
import com.driveforchange.app.viewmodel.StatsViewModel
import java.util.Locale

private fun formatMoney(value: Double): String = String.format(Locale.US, "$%.2f", value)
private fun formatMiles(value: Double): String = String.format(Locale.US, "%.1f mi", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.granularity, state.metric, state.periods.size) {
        selectedIndex = (state.periods.size - 1).coerceAtLeast(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatsGranularity.entries.forEach { option ->
                            FilterChip(
                                selected = state.granularity == option,
                                onClick = { viewModel.setGranularity(option) },
                                label = { Text(option.label()) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatsMetric.entries.forEach { option ->
                            FilterChip(
                                selected = state.metric == option,
                                onClick = { viewModel.setMetric(option) },
                                label = { Text(option.label()) },
                            )
                        }
                    }
                }

                if (state.loaded && state.periods.isNotEmpty()) {
                    val selected = state.periods.getOrNull(selectedIndex)
                    val barColor = if (state.metric == StatsMetric.DONATION) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    }

                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = "Total: ${formatForMetric(state.metric, state.totalMiles, state.totalDonation)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (selected != null) {
                            Text(
                                text = "${selected.fullLabel}: ${formatForMetric(state.metric, selected.miles, selected.donation)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    BarChart(
                        periods = state.periods,
                        valueSelector = { if (state.metric == StatsMetric.DONATION) it.donation else it.miles },
                        barColor = barColor,
                        selectedIndex = selectedIndex,
                        onBarTap = { selectedIndex = it },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    AxisLabelRow(periods = state.periods, modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        items(state.periods.reversed()) { period ->
                            PeriodRow(period = period)
                        }
                    }
                }
            }
        }
    }
}

private fun formatForMetric(metric: StatsMetric, miles: Double, donation: Double): String =
    if (metric == StatsMetric.DONATION) formatMoney(donation) else formatMiles(miles)

private fun StatsGranularity.label(): String = when (this) {
    StatsGranularity.DAY -> "Day"
    StatsGranularity.MONTH -> "Month"
    StatsGranularity.YEAR -> "Year"
}

private fun StatsMetric.label(): String = when (this) {
    StatsMetric.DONATION -> "Donation"
    StatsMetric.MILES -> "Miles"
}

@Composable
private fun PeriodRow(period: StatsPeriod) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(period.fullLabel, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                formatMiles(period.miles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                formatMoney(period.donation),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AxisLabelRow(periods: List<StatsPeriod>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        periods.forEach { period ->
            Text(
                text = period.axisLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BarChart(
    periods: List<StatsPeriod>,
    valueSelector: (StatsPeriod) -> Double,
    barColor: Color,
    selectedIndex: Int,
    onBarTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val dimColor = barColor.copy(alpha = 0.45f)
    val maxBarWidthDp = 24.dp
    val cornerRadiusDp = 4.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(periods.size) {
                detectTapGestures { offset ->
                    if (periods.isEmpty()) return@detectTapGestures
                    val slotWidth = size.width.toFloat() / periods.size
                    val index = (offset.x / slotWidth).toInt().coerceIn(0, periods.size - 1)
                    onBarTap(index)
                }
            }
    ) {
        if (periods.isEmpty()) return@Canvas

        val maxValue = periods.maxOf(valueSelector).coerceAtLeast(0.01)
        val slotWidth = size.width / periods.size
        val maxBarWidthPx = maxBarWidthDp.toPx()
        val barWidth = (slotWidth * 0.6f).coerceAtMost(maxBarWidthPx)
        val cornerRadiusPx = cornerRadiusDp.toPx()
        val baselineY = size.height
        val usableHeight = size.height - 8.dp.toPx()

        drawLine(
            color = baselineColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx(),
        )

        periods.forEachIndexed { index, period ->
            val value = valueSelector(period)
            val barHeight = ((value / maxValue) * usableHeight).toFloat().coerceAtLeast(2f)
            val slotCenter = slotWidth * index + slotWidth / 2
            val left = slotCenter - barWidth / 2
            val right = slotCenter + barWidth / 2
            val top = baselineY - barHeight
            val color = if (index == selectedIndex) barColor else dimColor

            val path = Path().apply {
                val r = cornerRadiusPx.coerceAtMost(barWidth / 2).coerceAtMost(barHeight)
                moveTo(left, baselineY)
                lineTo(left, top + r)
                quadraticTo(left, top, left + r, top)
                lineTo(right - r, top)
                quadraticTo(right, top, right, top + r)
                lineTo(right, baselineY)
                close()
            }
            drawPath(path, color)
        }
    }
}
