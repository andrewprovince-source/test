package com.driveforchange.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveforchange.app.data.DonationRepository
import com.driveforchange.app.data.local.DailyDonationEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class StatsGranularity { DAY, MONTH, YEAR }
enum class StatsMetric { DONATION, MILES }

/** One bar's worth of data. [axisLabel] is short (fits under a bar); [fullLabel] is used in taps/the list below. */
data class StatsPeriod(
    val axisLabel: String,
    val fullLabel: String,
    val miles: Double,
    val donation: Double,
)

data class StatsUiState(
    val granularity: StatsGranularity = StatsGranularity.DAY,
    val metric: StatsMetric = StatsMetric.DONATION,
    val periods: List<StatsPeriod> = emptyList(),
    val totalMiles: Double = 0.0,
    val totalDonation: Double = 0.0,
    val loaded: Boolean = false,
)

private const val DAY_WINDOW = 14
private const val MONTH_WINDOW = 12

class StatsViewModel(
    donationRepository: DonationRepository,
) : ViewModel() {

    private val granularity = MutableStateFlow(StatsGranularity.DAY)
    private val metric = MutableStateFlow(StatsMetric.DONATION)

    val uiState: StateFlow<StatsUiState> = combine(
        donationRepository.dailyRecordsFlow(),
        granularity,
        metric,
    ) { records, gran, met ->
        val periods = when (gran) {
            StatsGranularity.DAY -> buildDayPeriods(records)
            StatsGranularity.MONTH -> buildMonthPeriods(records)
            StatsGranularity.YEAR -> buildYearPeriods(records)
        }
        StatsUiState(
            granularity = gran,
            metric = met,
            periods = periods,
            totalMiles = periods.sumOf { it.miles },
            totalDonation = periods.sumOf { it.donation },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun setGranularity(value: StatsGranularity) {
        granularity.value = value
    }

    fun setMetric(value: StatsMetric) {
        metric.value = value
    }
}

private fun buildDayPeriods(records: List<DailyDonationEntity>): List<StatsPeriod> {
    val byDate = records.associateBy { it.date }
    val today = LocalDate.now()
    val axisFormatter = DateTimeFormatter.ofPattern("d", Locale.US)
    val fullFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    return (DAY_WINDOW - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val record = byDate[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]
        StatsPeriod(
            axisLabel = date.format(axisFormatter),
            fullLabel = date.format(fullFormatter),
            miles = record?.miles ?: 0.0,
            donation = record?.donation ?: 0.0,
        )
    }
}

private fun buildMonthPeriods(records: List<DailyDonationEntity>): List<StatsPeriod> {
    val grouped = records.groupBy { it.date.substring(0, 7) } // yyyy-MM
    val thisMonth = YearMonth.now()
    val axisFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
    val fullFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    return (MONTH_WINDOW - 1 downTo 0).map { offset ->
        val month = thisMonth.minusMonths(offset.toLong())
        val monthRecords = grouped[month.toString()].orEmpty()
        StatsPeriod(
            axisLabel = month.format(axisFormatter),
            fullLabel = month.format(fullFormatter),
            miles = monthRecords.sumOf { it.miles },
            donation = monthRecords.sumOf { it.donation },
        )
    }
}

private fun buildYearPeriods(records: List<DailyDonationEntity>): List<StatsPeriod> {
    val grouped = records.groupBy { it.date.substring(0, 4) } // yyyy
    val currentYear = LocalDate.now().year
    val years = (grouped.keys.map { it.toInt() } + currentYear).distinct().sorted()

    return years.map { year ->
        val yearRecords = grouped[year.toString()].orEmpty()
        StatsPeriod(
            axisLabel = year.toString(),
            fullLabel = year.toString(),
            miles = yearRecords.sumOf { it.miles },
            donation = yearRecords.sumOf { it.donation },
        )
    }
}
