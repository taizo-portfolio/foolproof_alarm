package com.foolproof.app

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("CalendarSyncWorker", "定期同期開始")
        try {
            val scheduler = AlarmScheduler(applicationContext)
            scheduler.rescheduleAllAlarms()

            Log.d("CalendarSyncWorker", "定期同期完了")
            Result.success()
        } catch (e: Exception) {
            Log.e("CalendarSyncWorker", "定期同期エラー", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "calendar_sync_work"

        // 定期実行を開始
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                10, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d("CalendarSyncWorker", "定期実行を登録")
        }

        // 定期実行を停止
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("CalendarSyncWorker", "定期実行を停止")
        }
    }
}