package com.foolproof.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.foolproof.app.data.AppDatabase
import com.foolproof.app.data.FiredAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra("EVENT_ID", -1)
        if (eventId == -1L) return

        Log.d("AlarmReceiver", "アラーム受信 ID: $eventId。サービスを開始します。")

        // 発火済みとしてDBに記録
        val db = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            db.firedAlarmDao().insert(FiredAlarm(eventId))
        }

        // AlarmServiceをフォアグラウンドで起動
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("EVENT_ID", eventId)
            putExtra("EVENT_TITLE", intent.getStringExtra("EVENT_TITLE"))
            putExtra("EVENT_START_TIME", intent.getLongExtra("EVENT_START_TIME", 0))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}