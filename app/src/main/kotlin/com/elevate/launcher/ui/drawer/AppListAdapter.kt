package com.elevate.launcher.ui.drawer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elevate.launcher.databinding.ItemDrawerAppBinding
import com.elevate.launcher.databinding.ItemDrawerHeaderBinding
import com.elevate.launcher.model.DrawerRow

class AppListAdapter(
    private val onAppClick: (DrawerRow.App) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var fullList: List<DrawerRow> = emptyList()
    private var visibleList: List<DrawerRow> = emptyList()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }

    fun submitList(rows: List<DrawerRow>) {
        fullList = rows
        visibleList = rows
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        if (query.isBlank()) {
            visibleList = fullList
            notifyDataSetChanged()
            return
        }
        val q = query.trim().lowercase()
        visibleList = fullList.filterIsInstance<DrawerRow.App>()
            .filter { it.item.label.lowercase().contains(q) }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        when (visibleList[position]) {
            is DrawerRow.Header -> TYPE_HEADER
            is DrawerRow.App -> TYPE_APP
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemDrawerHeaderBinding.inflate(inflater, parent, false))
        } else {
            AppViewHolder(ItemDrawerAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = visibleList[position]) {
            is DrawerRow.Header -> (holder as HeaderViewHolder).bind(row)
            is DrawerRow.App -> (holder as AppViewHolder).bind(row)
        }
    }

    override fun getItemCount(): Int = visibleList.size

    inner class HeaderViewHolder(private val binding: ItemDrawerHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DrawerRow.Header) {
            binding.root.text = row.title
        }
    }

    inner class AppViewHolder(private val binding: ItemDrawerAppBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DrawerRow.App) {
            binding.textLabel.text = row.item.label
            binding.imageIcon.setImageDrawable(row.item.icon)
            binding.root.setOnClickListener { onAppClick(row) }
        }
    }
}
