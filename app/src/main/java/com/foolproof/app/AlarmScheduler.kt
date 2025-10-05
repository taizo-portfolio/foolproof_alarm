package com.foolproof.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foolproof.app.data.AppDatabase
import java.util.*

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // アラームを個別に設定する
    fun scheduleAlarm(event: CalendarEvent, alarmTime: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.foolproof.app.ALARM_TRIGGER" // Actionを設定してIntentを明確にする
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_START_TIME", event.startTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime,
                pendingIntent
            )
            Log.d("AlarmScheduler", "✅ アラーム設定完了: [${event.title}] at ${Date(alarmTime)}")
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "❌ アラーム設定エラー: [${event.title}]", e)
        }
    }

    // アラームを個別にキャンセルする
    fun cancelAlarm(eventId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.foolproof.app.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("AlarmScheduler", "🗑️ アラームキャンセル: EventID=$eventId")
    }

    // すべてのアラームを再スケジュールする（メインのロジック）
    suspend fun rescheduleAllAlarms() {
        Log.d("AlarmScheduler", "--- 🔄 アラームの再スケジュールを開始 ---")
        val prefs = AppPreferences(context)
        val calendarHelper = CalendarHelper(context)
        val db = AppDatabase.getDatabase(context)
        val dao = db.firedAlarmDao()

        val selectedIds = prefs.selectedCalendarIds
        if (selectedIds.isEmpty()) {
            Log.d("AlarmScheduler", "⚠️ 対象カレンダーが選択されていないため、処理を中断します。")
            return
        }

        // 1. カレンダーから最新の予定リストを取得
        val upcomingEvents = calendarHelper.getUpcomingEvents(selectedIds, 7)
        Log.d("AlarmScheduler", "🔍 カレンダーから ${upcomingEvents.size} 件の予定を発見しました。")

        val newEventIds = upcomingEvents.map { it.id.toString() }.toSet()
        val oldEventIds = prefs.scheduledEventIds

        // 2. 不要になったアラームをキャンセル (前回設定したが、今回は存在しない予定)
        val eventsToCancel = oldEventIds - newEventIds
        if (eventsToCancel.isNotEmpty()) {
            Log.d("AlarmScheduler", "🚫 不要なアラーム ${eventsToCancel.size} 件をキャンセルします: $eventsToCancel")
            eventsToCancel.forEach { cancelAlarm(it.toLong()) }
        }

        val minutesBefore = prefs.alarmMinutesBefore
        val timeLimitHour = prefs.timeLimitHour
        var scheduledCount = 0

        // 3. 新しい予定リストに基づいてアラームを設定
        for (event in upcomingEvents) {
            // 既に発火済みかチェック
            if (dao.isFired(event.id)) {
                Log.d("AlarmScheduler", "   -> スキップ (発火済み): [${event.title}]")
                continue
            }

            // 時刻制限チェック
            val eventCalendar = Calendar.getInstance().apply { timeInMillis = event.startTime }
            val eventHour = eventCalendar.get(Calendar.HOUR_OF_DAY)
            if (eventHour >= timeLimitHour) {
                Log.d("AlarmScheduler", "   -> スキップ (時刻制限対象): [${event.title}] at ${eventHour}時")
                continue
            }

            // アラーム時刻を計算
            val alarmTime = event.startTime - (minutesBefore * 60 * 1000L)

            // 現在時刻より未来のアラームのみ設定
            if (alarmTime > System.currentTimeMillis()) {
                scheduleAlarm(event, alarmTime)
                scheduledCount++
            } else {
                Log.d("AlarmScheduler", "   -> スキップ (過去のアラーム): [${event.title}]")
            }
        }

        // 4. 今回スケジュールしたイベントIDのリストを保存
        prefs.scheduledEventIds = newEventIds
        Log.d("AlarmScheduler", "--- ✅ 再スケジュール完了 (新規/更新: ${scheduledCount}件) ---")
    }
}