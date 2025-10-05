package com.foolproof.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "端末起動を検知")

            // アラームを再設定
            CoroutineScope(Dispatchers.IO).launch {
                val scheduler = AlarmScheduler(context)
                scheduler.rescheduleAllAlarms()
            }
        }
    }
}
