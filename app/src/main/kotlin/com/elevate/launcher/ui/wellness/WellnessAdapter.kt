package com.elevate.launcher.ui.wellness

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elevate.launcher.data.StreakPrefs
import com.elevate.launcher.databinding.ItemWellnessBinding
import com.elevate.launcher.model.WellnessItem

class WellnessAdapter(
    private val items: List<WellnessItem>,
    private val streakPrefs: StreakPrefs
) : RecyclerView.Adapter<WellnessAdapter.ViewHolder>() {

    private val expandedIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWellnessBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemWellnessBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WellnessItem) {
            binding.textTitle.text = item.title
            binding.textSummary.text = item.summary

            val expanded = expandedIds.contains(item.id)
            applyExpandedState(expanded, item)

            binding.rowHeader.setOnClickListener {
                if (expandedIds.contains(item.id)) expandedIds.remove(item.id) else expandedIds.add(item.id)
                applyExpandedState(expandedIds.contains(item.id), item)
            }

            refreshStreak(item.id)

            binding.buttonMarkDone.setOnClickListener {
                streakPrefs.markDoneToday(item.id)
                refreshStreak(item.id)
            }
        }

        private fun applyExpandedState(expanded: Boolean, item: WellnessItem) {
            binding.textExpandIcon.text = if (expanded) "▴" else "▾"
            if (expanded && item.steps.isNotEmpty()) {
                binding.textSteps.visibility = View.VISIBLE
                binding.textSteps.text = item.steps
                    .mapIndexed { index, step -> "${index + 1}. $step" }
                    .joinToString("\n")
                binding.rowActions.visibility = View.VISIBLE
            } else {
                binding.textSteps.visibility = View.GONE
                // Simple grooming tips without steps still get a mark-done action when expanded.
                binding.rowActions.visibility = if (expanded) View.VISIBLE else View.GONE
            }
        }

        private fun refreshStreak(itemId: String) {
            val streak = streakPrefs.getStreak(itemId)
            val doneToday = streakPrefs.isDoneToday(itemId)
            binding.textStreak.text = if (streak > 0)
                binding.root.context.getString(com.elevate.launcher.R.string.streak_format, streak)
            else
                binding.root.context.getString(com.elevate.launcher.R.string.streak_zero)
            binding.buttonMarkDone.isEnabled = !doneToday
            binding.buttonMarkDone.text = if (doneToday)
                binding.root.context.getString(com.elevate.launcher.R.string.done_today)
            else
                binding.root.context.getString(com.elevate.launcher.R.string.mark_done_today)
        }
    }
}
