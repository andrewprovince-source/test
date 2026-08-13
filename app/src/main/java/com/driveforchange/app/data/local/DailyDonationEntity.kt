package com.driveforchange.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per calendar day (date stored as "yyyy-MM-dd"). Miles and donation accumulate
 * throughout the day as location updates arrive; past days are frozen once the day ends.
 */
@Entity(tableName = "daily_donations")
data class DailyDonationEntity(
    @PrimaryKey val date: String,
    val miles: Double,
    val donation: Double,
)
