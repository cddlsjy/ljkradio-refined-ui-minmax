package com.example.ijkradio.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ijkradio.R
import com.example.ijkradio.data.Station

/**
 * 电台列表适配器
 * 使用 ListAdapter 和 DiffUtil 实现高效的列表更新
 */
class StationAdapter(
    private val onStationClick: (Station) -> Unit,
    private val onDeleteClick: (Station) -> Unit
) : ListAdapter<Station, StationAdapter.StationViewHolder>(StationDiffCallback()) {

    // 当前选中的电台ID
    private var selectedStationId: String? = null

    // 正在播放的电台ID
    private var playingStationId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = getItem(position)
        holder.bind(station, station.id == selectedStationId, station.id == playingStationId)
    }

    /**
     * 设置选中的电台
     */
    fun setSelectedStation(station: Station?) {
        val oldSelectedId = selectedStationId
        selectedStationId = station?.id

        // 更新旧选中项
        oldSelectedId?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }

        // 更新新选中项
        station?.id?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }
    }

    /**
     * 设置正在播放的电台
     */
    fun setPlayingStation(station: Station?) {
        val oldPlayingId = playingStationId
        playingStationId = station?.id

        // 更新旧播放项
        oldPlayingId?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }

        // 更新新播放项
        station?.id?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }
    }

    /**
     * 刷新列表
     */
    fun refreshList() {
        notifyDataSetChanged()
    }

    /**
     * 获取当前选中的电台
     */
    fun getSelectedStation(): Station? {
        return selectedStationId?.let { id ->
            currentList.find { it.id == id }
        }
    }

    /**
     * 获取指定位置的电台
     */
    fun getItemAt(position: Int): Station? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.station_name)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.station_description)
        private val playingIndicator: View = itemView.findViewById(R.id.playing_indicator)

        fun bind(station: Station, isSelected: Boolean, isPlaying: Boolean) {
            nameTextView.text = station.name
            descriptionTextView.text = station.getDisplayName()
            descriptionTextView.visibility = if (station.description.isNotBlank()) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 设置选中状态
            itemView.isSelected = isSelected

            // 设置播放状态
            playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

            // 设置背景
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.bg_station_selected
                else R.drawable.bg_station_normal
            )

            // 点击事件
            itemView.setOnClickListener {
                onStationClick(station)
            }
        }
    }

    /**
     * DiffUtil 回调
     */
    class StationDiffCallback : DiffUtil.ItemCallback<Station>() {
        override fun areItemsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem == newItem
        }
    }
}
