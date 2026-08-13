package com.driveforchange.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyDonationDao {

    @Upsert
    suspend fun upsert(entity: DailyDonationEntity)

    @Query("SELECT * FROM daily_donations WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyDonationEntity?

    @Query("SELECT * FROM daily_donations WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyDonationEntity?>

    @Query("SELECT * FROM daily_donations ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyDonationEntity>>
}
