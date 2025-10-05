package com.foolproof.app

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import java.util.*

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val calendarId: Long
)

class CalendarHelper(private val context: Context) {

    // カレンダー一覧を取得
    fun getCalendarList(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        try {
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1) ?: "不明"
                    val accountName = it.getString(2) ?: "不明"
                    calendars.add(CalendarInfo(id, name, accountName))
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarHelper", "カレンダー取得エラー", e)
        }
        return calendars
    }

    // ★★★ この関数が全面的に修正されています ★★★
    // 指定期間のイベントを取得 (繰り返しの予定を正しく展開する方式)
    fun getEvents(
        calendarIds: Set<String>,
        startTime: Long,
        endTime: Long
    ): List<CalendarEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        val events = mutableListOf<CalendarEvent>()

        // 取得する情報の種類を定義
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID
        )

        // 繰り返しの予定を展開するためのURIを作成
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startTime)
        ContentUris.appendId(builder, endTime)

        // 絞り込み条件を定義
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",")}) " +
                "AND ${CalendarContract.Instances.ALL_DAY} = 0 " + // 終日の予定は除外
                "AND ${CalendarContract.Instances.TITLE} IS NOT NULL AND ${CalendarContract.Instances.TITLE} != '' " +
                "AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}" // 「不参加」の予定は除外

        try {
            // データベースに問い合わせ
            val cursor: Cursor? = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC" // 開始時間順にソート
            )

            cursor?.use {
                val idCol = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleCol = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginCol = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endCol = it.getColumnIndex(CalendarContract.Instances.END)
                val calIdCol = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol)
                    val start = it.getLong(beginCol)
                    val end = it.getLong(endCol)
                    val calId = it.getLong(calIdCol)
                    events.add(CalendarEvent(id, title, start, end, calId))
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarHelper", "イベント取得エラー", e)
        }
        return events
    }

    // 今日から指定日数後までのイベントを取得
    fun getUpcomingEvents(calendarIds: Set<String>, daysAhead: Int = 7): List<CalendarEvent> {
        val calendar = Calendar.getInstance()
        // 7日間の範囲を正確に設定
        val startTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
        val endTime = calendar.timeInMillis
        return getEvents(calendarIds, startTime, endTime)
    }
}