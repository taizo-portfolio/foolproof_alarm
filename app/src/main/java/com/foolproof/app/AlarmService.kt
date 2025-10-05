package com.foolproof.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var originalVolume: Int = 0

    companion object {
        const val ACTION_STOP_ALARM = "com.foolproof.app.STOP_ALARM"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val eventTitle = intent?.getStringExtra("EVENT_TITLE") ?: "予定"
        val eventStartTime = intent?.getLongExtra("EVENT_START_TIME", 0) ?: 0
        val eventId = intent?.getLongExtra("EVENT_ID", -1) ?: -1

        // フォアグラウンドサービスを開始
        startForeground(1, createNotification(eventTitle, eventId, eventStartTime))

        // アラーム音とバイブレーションを開始
        playAlarm(this)

        return START_STICKY
    }

    private fun playAlarm(context: Context) {
        // --- 強制的に最大音量にする ---
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // --- アラーム音を再生 ---
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioStreamType(AudioManager.STREAM_ALARM) // アラーム音量で再生
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // --- バイブレーションを開始 ---
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 1000, 500) // 0ms待機, 1000ms振動, 500ms待機
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0で繰り返し
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        // --- アラーム音を停止し、音量を元に戻す ---
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)

        // --- バイブレーションを停止 ---
        vibrator?.cancel()
        vibrator = null

        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(eventTitle: String, eventId: Long, eventStartTime: Long): Notification {
        val channelId = "alarm_service_channel"
        val channelName = "アラームサービス"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "アラーム実行中の通知"
                setBypassDnd(true) // DNDモードを貫通
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("EVENT_TITLE", eventTitle)
            putExtra("EVENT_START_TIME", eventStartTime)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, eventId.toInt(), fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("アラーム")
            .setContentText(eventTitle)
            .setSmallIcon(R.drawable.ic_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // これが強制的に画面を起動するキー
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}