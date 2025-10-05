package com.foolproof.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.foolproof.app.databinding.ActivityAlarmBinding
import java.text.SimpleDateFormat
import java.util.*

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面より手前に表示し、画面をONにする設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intentからデータを取得してUIに表示
        val eventTitle = intent.getStringExtra("EVENT_TITLE") ?: "予定"
        val eventStartTime = intent.getLongExtra("EVENT_START_TIME", 0)
        binding.eventTitleText.text = eventTitle
        binding.eventTimeText.text = "${dateFormat.format(Date(eventStartTime))} 開始"

        // 停止ボタンが押されたらAlarmServiceに停止を命令
        binding.stopButton.setOnClickListener {
            val stopIntent = Intent(this, AlarmService::class.java)
            stopIntent.action = AlarmService.ACTION_STOP_ALARM
            startService(stopIntent)
            finish()
        }
    }

    override fun onBackPressed() {
        // 戻るボタンを無効化
    }
}