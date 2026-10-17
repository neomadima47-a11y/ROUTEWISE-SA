package com.routewise.sa.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.routewise.sa.R

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val impactBonus: Int,
    val isRead: Boolean
)

class NotificationAdapter(
    private val items: List<NotificationItem>,
    private val onZoomMap: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNotifTitle: TextView = view.findViewById(R.id.tvNotifTitle)
        val tvNotifMessage: TextView = view.findViewById(R.id.tvNotifMessage)
        val tvNotifBonus: TextView = view.findViewById(R.id.tvNotifBonus)
        val tvNotifTime: TextView = view.findViewById(R.id.tvNotifTime)
        val btnNotifZoomMap: MaterialButton = view.findViewById(R.id.btnNotifZoomMap)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvNotifTitle.text = item.title
        holder.tvNotifMessage.text = item.message
        holder.tvNotifBonus.text = "+${item.impactBonus} PTS"
        holder.tvNotifTime.text = "Just now"

        holder.btnNotifZoomMap.setOnClickListener {
            onZoomMap(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
