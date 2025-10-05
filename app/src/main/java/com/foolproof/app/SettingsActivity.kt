package com.foolproof.app

import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.foolproof.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences
    private val calendarCheckBoxes = mutableMapOf<Long, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        loadSettings()
        loadCalendarList()
        setupSaveButton()
    }

    private fun loadSettings() {
        binding.minutesBeforeEdit.setText(prefs.alarmMinutesBefore.toString())
        binding.timeLimitEdit.setText(prefs.timeLimitHour.toString())
    }

    private fun loadCalendarList() {
        lifecycleScope.launch {
            val calendars = withContext(Dispatchers.IO) {
                val helper = CalendarHelper(this@SettingsActivity)
                helper.getCalendarList()
            }

            withContext(Dispatchers.Main) {
                displayCalendarList(calendars)
            }
        }
    }

    private fun displayCalendarList(calendars: List<CalendarInfo>) {
        binding.calendarListContainer.removeAllViews()
        calendarCheckBoxes.clear()

        val selectedIds = prefs.selectedCalendarIds

        for (calendar in calendars) {
            val checkBox = CheckBox(this).apply {
                text = "${calendar.name} (${calendar.accountName})"
                textSize = 16f
                setPadding(0, 16, 0, 16)
                isChecked = selectedIds.contains(calendar.id.toString())
            }

            calendarCheckBoxes[calendar.id] = checkBox
            binding.calendarListContainer.addView(checkBox)
        }

        if (calendars.isEmpty()) {
            val emptyView = android.widget.TextView(this).apply {
                text = "カレンダーが見つかりません"
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            binding.calendarListContainer.addView(emptyView)
        }
    }

    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        // 入力値の検証
        val minutesBeforeText = binding.minutesBeforeEdit.text.toString()
        val timeLimitText = binding.timeLimitEdit.text.toString()

        if (minutesBeforeText.isEmpty() || timeLimitText.isEmpty()) {
            Toast.makeText(this, "すべての項目を入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        val minutesBefore = minutesBeforeText.toIntOrNull()
        val timeLimit = timeLimitText.toIntOrNull()

        if (minutesBefore == null || minutesBefore !in 1..180) {
            Toast.makeText(this, "アラーム通知時間は1〜180分の範囲で設定してください", Toast.LENGTH_SHORT).show()
            return
        }

        if (timeLimit == null || timeLimit !in 0..23) {
            Toast.makeText(this, "時刻制限は0〜23時の範囲で設定してください", Toast.LENGTH_SHORT).show()
            return
        }

        // 選択されたカレンダーIDを取得
        val selectedIds = mutableSetOf<String>()
        for ((calendarId, checkBox) in calendarCheckBoxes) {
            if (checkBox.isChecked) {
                selectedIds.add(calendarId.toString())
            }
        }

        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "少なくとも1つのカレンダーを選択してください", Toast.LENGTH_SHORT).show()
            return
        }

        // 設定を保存
        prefs.alarmMinutesBefore = minutesBefore
        prefs.timeLimitHour = timeLimit
        prefs.selectedCalendarIds = selectedIds

        // アラームを再設定
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val scheduler = AlarmScheduler(this@SettingsActivity)
                scheduler.rescheduleAllAlarms()
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, "設定を保存しました", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
