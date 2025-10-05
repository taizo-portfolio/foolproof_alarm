package com.foolproof.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fired_alarms")
data class FiredAlarm(
    @PrimaryKey
    val eventId: Long,
    val firedAt: Long = System.currentTimeMillis()
)
