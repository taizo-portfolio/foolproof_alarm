package com.foolproof.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.foolproof.app.data.AppDatabase // ← ★★★ この行が追加されています ★★★
import com.foolproof.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter
    private lateinit var prefs: AppPreferences

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                // すべての権限が許可された
                checkExactAlarmPermission()
            } else {
                Toast.makeText(this, "カレンダーの読み取り権限が必要です", Toast.LENGTH_LONG).show()
                binding.statusText.text = "カレンダー権限がありません"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)
        setupRecyclerView()
        setupButtons()

        // 権限チェックの呼び出し
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // onResumeのタイミングでUIを更新することで、設定画面から戻ってきた時などに
        // 権限の状態を再チェックしてUIを更新できる
        updateUiBasedOnPermissions()
    }

    private fun setupRecyclerView() {
        adapter = AlarmAdapter()
        binding.alarmRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.alarmRecyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // カレンダー権限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CALENDAR)
        }
        // 通知権限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // すでに全ての権限が許可されている場合
            checkExactAlarmPermission()
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // 許可されていない場合、設定画面に誘導
                binding.statusText.text = "アラームの権限を許可してください"
                Toast.makeText(this, "正確なアラームの許可が必要です", Toast.LENGTH_LONG).show()
                Intent().also { intent ->
                    intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(intent)
                }
            } else {
                // 許可されている場合
                onAllPermissionsGranted()
            }
        } else {
            // Android 12未満は権限不要
            onAllPermissionsGranted()
        }
    }

    // onResumeで呼ばれるUI更新用の関数
    private fun updateUiBasedOnPermissions() {
        val hasCalendarPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (hasCalendarPermission && alarmManager.canScheduleExactAlarms()) {
                onAllPermissionsGranted()
            } else if (!hasCalendarPermission) {
                binding.statusText.text = "カレンダー権限がありません"
            } else {
                binding.statusText.text = "アラームの権限を許可してください"
            }
        } else {
            if (hasCalendarPermission) {
                onAllPermissionsGranted()
            } else {
                binding.statusText.text = "カレンダー権限がありません"
            }
        }
    }


    private fun onAllPermissionsGranted() {
        binding.statusText.text = "正常に動作中"
        // 定期実行を開始
        CalendarSyncWorker.schedule(this)
        // アラームの再設定とUI更新
        lifecycleScope.launch {
            val scheduler = AlarmScheduler(this@MainActivity)
            scheduler.rescheduleAllAlarms()
            updateAlarmListUi()
        }
    }

    private suspend fun updateAlarmListUi() {
        val alarmItems = loadAlarmItems()
        withContext(Dispatchers.Main) {
            if (alarmItems.isEmpty()) {
                binding.alarmRecyclerView.visibility = View.GONE
                binding.emptyText.visibility = View.VISIBLE
            } else {
                binding.alarmRecyclerView.visibility = View.VISIBLE
                binding.emptyText.visibility = View.GONE
                adapter.updateItems(alarmItems)
            }
        }
    }


    private suspend fun loadAlarmItems(): List<AlarmItem> = withContext(Dispatchers.IO) {
        val selectedIds = prefs.selectedCalendarIds
        if (selectedIds.isEmpty()) return@withContext emptyList()

        val calendarHelper = CalendarHelper(this@MainActivity)
        val db = AppDatabase.getDatabase(this@MainActivity)
        val dao = db.firedAlarmDao()

        val events = calendarHelper.getUpcomingEvents(selectedIds, 7)
        val minutesBefore = prefs.alarmMinutesBefore
        val timeLimitHour = prefs.timeLimitHour

        val alarmItems = mutableListOf<AlarmItem>()
        for (event in events) {
            if (dao.isFired(event.id)) continue

            val eventCalendar = Calendar.getInstance().apply { timeInMillis = event.startTime }
            val eventHour = eventCalendar.get(Calendar.HOUR_OF_DAY)
            if (eventHour >= timeLimitHour) continue

            val alarmTime = event.startTime - (minutesBefore * 60 * 1000L)

            if (alarmTime > System.currentTimeMillis()) {
                alarmItems.add(
                    AlarmItem(
                        event.id,
                        event.title,
                        event.startTime,
                        alarmTime
                    )
                )
            }
        }
        alarmItems.sortBy { it.alarmTime }
        return@withContext alarmItems
    }
}