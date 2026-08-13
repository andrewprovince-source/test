package com.driveforchange.app.data

import android.content.Context
import com.driveforchange.app.data.local.AppDatabase

/** Simple manual dependency container (no DI framework needed for a prototype this size). */
class AppContainer(context: Context) {
    val userPreferencesRepository = UserPreferencesRepository(context)

    private val database = AppDatabase.getInstance(context)

    val donationRepository = DonationRepository(
        dao = database.dailyDonationDao(),
        userPreferencesRepository = userPreferencesRepository,
    )
}
