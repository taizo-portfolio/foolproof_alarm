package com.foolproof.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class AlarmItem(
    val eventId: Long,
    val title: String,
    val startTime: Long,
    val alarmTime: Long
)

class AlarmAdapter : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    private val items = mutableListOf<AlarmItem>()
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventTitle: TextView = view.findViewById(R.id.eventTitle)
        val eventStartTime: TextView = view.findViewById(R.id.eventStartTime)
        val alarmTime: TextView = view.findViewById(R.id.alarmTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val item = items[position]
        holder.eventTitle.text = item.title
        holder.eventStartTime.text = "${dateFormat.format(Date(item.startTime))} 開始"
        holder.alarmTime.text = "${dateFormat.format(Date(item.alarmTime))} アラーム"
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<AlarmItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
