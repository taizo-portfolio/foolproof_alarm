package com.foolproof.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FiredAlarmDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(firedAlarm: FiredAlarm)

    @Query("SELECT EXISTS(SELECT 1 FROM fired_alarms WHERE eventId = :eventId)")
    suspend fun isFired(eventId: Long): Boolean

    @Query("SELECT * FROM fired_alarms")
    suspend fun getAllFiredAlarms(): List<FiredAlarm>

    @Query("DELETE FROM fired_alarms WHERE firedAt < :cutoffTime")
    suspend fun deleteOldRecords(cutoffTime: Long)
}
