package com.driveforchange.app.data

import com.driveforchange.app.data.local.DailyDonationDao
import com.driveforchange.app.data.local.DailyDonationEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.math.min

data class DonationTotals(
    val todayMiles: Double,
    val todayDonation: Double,
    val monthDonation: Double,
    val yearDonation: Double,
    val lifetimeDonation: Double,
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

class DonationRepository(
    private val dao: DailyDonationDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    /** Adds newly driven distance to today's mock ledger entry, applying the current rate and daily cap. */
    suspend fun recordDistanceMiles(deltaMiles: Double) {
        if (deltaMiles <= 0.0) return

        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val today = LocalDate.now().format(DATE_FORMAT)
        val existing = dao.getByDate(today)
        val newMiles = (existing?.miles ?: 0.0) + deltaMiles
        val newDonation = min(newMiles * prefs.perMileRate, prefs.dailyCap)

        dao.upsert(DailyDonationEntity(date = today, miles = newMiles, donation = newDonation))
    }

    /**
     * Re-applies the current rate and cap to today's already-stored miles. Without this, a
     * lowered cap only takes effect on the next GPS update — today's total could keep showing
     * an amount from before the cap was lowered until the user drives again.
     */
    suspend fun reclampToday() {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val today = LocalDate.now().format(DATE_FORMAT)
        val existing = dao.getByDate(today) ?: return
        val clampedDonation = min(existing.miles * prefs.perMileRate, prefs.dailyCap)
        if (clampedDonation != existing.donation) {
            dao.upsert(existing.copy(donation = clampedDonation))
        }
    }

    /** Raw per-day ledger rows, newest first — the source for the stats screen's aggregations. */
    fun dailyRecordsFlow(): Flow<List<DailyDonationEntity>> = dao.observeAll()

    fun totalsFlow(): Flow<DonationTotals> {
        val today = LocalDate.now()
        val todayKey = today.format(DATE_FORMAT)
        val monthPrefix = todayKey.substring(0, 7) // yyyy-MM
        val yearPrefix = todayKey.substring(0, 4) // yyyy

        return dao.observeAll().combine(dao.observeByDate(todayKey)) { all, todayRecord ->
            DonationTotals(
                todayMiles = todayRecord?.miles ?: 0.0,
                todayDonation = todayRecord?.donation ?: 0.0,
                monthDonation = all.filter { it.date.startsWith(monthPrefix) }.sumOf { it.donation },
                yearDonation = all.filter { it.date.startsWith(yearPrefix) }.sumOf { it.donation },
                lifetimeDonation = all.sumOf { it.donation },
            )
        }
    }
}
