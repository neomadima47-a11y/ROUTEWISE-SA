package com.routewise.sa.ui.alerts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.routewise.sa.R
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.databinding.ItemIncidentBinding
import java.util.concurrent.TimeUnit

class IncidentAdapter(
    private val onUpvote: (IncidentEntity) -> Unit,
    private val onDismiss: (IncidentEntity) -> Unit,
    private val onViewOnMap: (IncidentEntity) -> Unit
) : ListAdapter<IncidentEntity, IncidentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIncidentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemIncidentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(incident: IncidentEntity) {
            binding.tvIncidentType.text = getIconForType(incident.type) + " " + incident.type
            binding.tvRoadName.text = incident.roadName
            binding.tvLocation.text = "${incident.city}, ${incident.province}"
            binding.tvDescription.text = incident.description
            binding.tvTimeAgo.text = formatTimeAgo(incident.timestamp)
            binding.btnHelpful.text = "👍 ${incident.upvotes} Helpful"

            // Route Badge
            if (incident.routeBadge != null) {
                binding.tvRouteBadge.visibility = View.VISIBLE
                binding.tvRouteBadge.text = incident.routeBadge
            } else {
                binding.tvRouteBadge.visibility = View.GONE
            }

            // Direction Badge
            if (incident.directionBadge != null) {
                binding.tvDirectionBadge.visibility = View.VISIBLE
                binding.tvDirectionBadge.text = incident.directionBadge
            } else {
                binding.tvDirectionBadge.visibility = View.GONE
            }

            // Severity Badge
            binding.tvSeverityBadge.text = "${incident.severity.capitalize()} Severity"
            val severityColor = when (incident.severity.toLowerCase()) {
                "critical", "high" -> R.drawable.bg_severity_critical
                else -> R.drawable.bg_severity_selected
            }
            binding.tvSeverityBadge.setBackgroundResource(severityColor)
            val textColor = when (incident.severity.toLowerCase()) {
                "critical", "high" -> R.color.hazard_accident_rose
                else -> R.color.primary_emerald
            }
            binding.tvSeverityBadge.setTextColor(ContextCompat.getColor(binding.root.context, textColor))

            // Status Box
            if (incident.statusBox.isNotEmpty()) {
                binding.layoutStatusBox.visibility = View.VISIBLE
                binding.tvStatusBox.text = incident.statusBox
            } else {
                binding.layoutStatusBox.visibility = View.GONE
            }

            // Clicks
            binding.btnHelpful.setOnClickListener { onUpvote(incident) }
            binding.btnDismiss.setOnClickListener { onDismiss(incident) }
            binding.btnViewOnMap.setOnClickListener { onViewOnMap(incident) }
        }

        private fun getIconForType(type: String): String {
            return when (type) {
                "Accident" -> "💥"
                "Broken Robot" -> "🚦"
                "Pothole" -> "🕳️"
                "Police Trap" -> "👮"
                "Roadworks" -> "🚧"
                "Closure" -> "⛔"
                else -> "⚠️"
            }
        }

        private fun formatTimeAgo(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                else -> "${minutes / 60}h ago"
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<IncidentEntity>() {
        override fun areItemsTheSame(oldItem: IncidentEntity, newItem: IncidentEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IncidentEntity, newItem: IncidentEntity): Boolean = oldItem == newItem
    }
}
