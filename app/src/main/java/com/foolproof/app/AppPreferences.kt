package com.foolproof.app

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("foolproof_prefs", Context.MODE_PRIVATE)

    // アラーム通知時間（分前）
    var alarmMinutesBefore: Int
        get() = prefs.getInt("alarm_minutes_before", 30)
        set(value) = prefs.edit().putInt("alarm_minutes_before", value).apply()

    // 時刻制限（何時以前）
    var timeLimitHour: Int
        get() = prefs.getInt("time_limit_hour", 10)
        set(value) = prefs.edit().putInt("time_limit_hour", value).apply()

    // 選択されたカレンダーID（カンマ区切り）
    var selectedCalendarIds: Set<String>
        get() = prefs.getStringSet("selected_calendar_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("selected_calendar_ids", value).apply()

    var scheduledEventIds: Set<String>
        get() = prefs.getStringSet("scheduled_event_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("scheduled_event_ids", value).apply()
}
