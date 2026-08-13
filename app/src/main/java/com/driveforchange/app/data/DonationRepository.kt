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
