package com.example.ijkradio.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.example.ijkradio.MainActivity
import com.example.ijkradio.R
import com.example.ijkradio.data.Station
import com.example.ijkradio.player.IPlayerManager
import com.bumptech.glide.Glide

class PlayerFullscreenFragment : Fragment() {

    private lateinit var stationIcon: ShapeableImageView
    private lateinit var textViewGeneralInfo: TextView
    private lateinit var textViewStationName: TextView
    private lateinit var buttonPlay: ImageButton
    private lateinit var buttonPrev: ImageButton
    private lateinit var buttonNext: ImageButton
    private var stationsRecyclerView: RecyclerView? = null
    private var stationAdapter: StationAdapter? = null

    private var playerManager: IPlayerManager? = null
    private var currentStation: Station? = null
    private var isPlaying = false
    private var displayMode: Int = 0
    private var logoShape: Int = 0
    private var backgroundColor: Int = 0

    private var isViewReady = false
    private var pendingStation: Station? = null
    private var stationsList: List<Station>? = null

    interface PlayerFullscreenListener {
        fun onExitFullscreen()
    }

    private var listener: PlayerFullscreenListener? = null

    fun setPlayerFullscreenListener(listener: PlayerFullscreenListener) {
        this.listener = listener
    }

    fun setDisplayMode(mode: Int) {
        this.displayMode = mode
    }

    fun setStationsList(stations: List<Station>) {
        this.stationsList = stations
        if (isViewReady && displayMode == 1) {
            stationAdapter?.submitList(stations)
        }
    }

    fun setLogoShape(shape: Int) {
        this.logoShape = shape
        if (isViewReady) {
            applyLogoShape()
        }
    }

    fun setBackgroundColor(color: Int) {
        this.backgroundColor = color
        if (isViewReady) {
            applyBackgroundColor()
        }
    }

    private fun applyLogoShape() {
        if (!::stationIcon.isInitialized) return
        when (logoShape) {
            0 -> { // 圆形
                stationIcon.shapeAppearanceModel = stationIcon.shapeAppearanceModel
                    .toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 999f)
                    .build()
            }
            1 -> { // 方型
                stationIcon.shapeAppearanceModel = stationIcon.shapeAppearanceModel
                    .toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build()
            }
        }
    }

    private fun applyBackgroundColor() {
        // 背景颜色控制将在布局加载时处理
        // 此方法可以用于动态调整其他UI元素
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 根据显示方案和背景颜色选择布局
        val baseLayout = when (displayMode) {
            1 -> R.layout.layout_player_fullscreen_portrait
            2 -> R.layout.layout_player_fullscreen_landscape
            else -> R.layout.layout_player_fullscreen
        }
        
        // 如果选择了白色背景，使用白色版本的布局
        val layoutRes = if (backgroundColor == 1) {
            R.layout.layout_player_fullscreen_light
        } else {
            baseLayout
        }
        return inflater.inflate(layoutRes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stationIcon = view.findViewById(R.id.stationIcon)
        textViewGeneralInfo = view.findViewById(R.id.textViewGeneralInfo)
        textViewStationName = view.findViewById(R.id.textViewStationName)
        buttonPlay = view.findViewById(R.id.buttonPlay)
        buttonPrev = view.findViewById(R.id.buttonPrev)
        buttonNext = view.findViewById(R.id.buttonNext)
        
        // 检查是否是竖屏带列表的布局
        stationsRecyclerView = view.findViewById(R.id.stationsRecyclerView)
        stationsRecyclerView?.let { rv ->
            stationAdapter = StationAdapter(
                onStationClick = { station ->
                    (activity as? MainActivity)?.let { activity ->
                        activity.handleStationClick(station)
                    }
                },
                onDeleteClick = { }
            )
            rv.layoutManager = LinearLayoutManager(context)
            rv.adapter = stationAdapter
            stationsList?.let { stationAdapter?.submitList(it) }
        }

        buttonPlay.setOnClickListener {
            togglePlayPause()
        }

        buttonPrev.setOnClickListener {
            (activity as? MainActivity)?.playPreviousStation()
        }

        buttonNext.setOnClickListener {
            (activity as? MainActivity)?.playNextStation()
        }

        view.findViewById<ImageButton>(R.id.buttonFullscreenExit)?.setOnClickListener {
            listener?.onExitFullscreen()
        }

        isViewReady = true
        pendingStation?.let { applyStationInfo(it) }
        pendingStation = null
        
        applyLogoShape()
        applyBackgroundColor()
    }

    fun setPlayerManager(manager: IPlayerManager) {
        this.playerManager = manager
    }

    fun updateStationInfo(station: Station?) {
        currentStation = station
        if (!isViewReady) {
            pendingStation = station
            return
        }
        applyStationInfo(station)
    }

    private fun applyStationInfo(station: Station?) {
        if (station != null) {
            textViewStationName.text = station.name
            textViewStationName.visibility = View.VISIBLE
            loadStationLogo(station)
            stationAdapter?.setSelectedStation(station)
        } else {
            textViewStationName.visibility = View.GONE
            stationIcon.setImageResource(R.drawable.ic_default_station_image_180dp)
        }
    }

    private fun loadStationLogo(station: Station) {
        if (!station.logoUrl.isNullOrEmpty()) {
            try {
                Glide.with(requireContext())
                    .load(station.logoUrl)
                    .placeholder(R.drawable.ic_default_station_image_180dp)
                    .error(R.drawable.ic_default_station_image_180dp)
                    .into(stationIcon)
            } catch (e: Exception) {
                stationIcon.setImageResource(R.drawable.ic_default_station_image_180dp)
            }
        } else {
            Glide.with(requireContext()).clear(stationIcon)
            stationIcon.setImageResource(R.drawable.ic_default_station_image_180dp)
        }
    }

    fun updatePlaybackState(state: PlaybackState) {
        if (!isViewReady) {
            Handler(Looper.getMainLooper()).postDelayed({ updatePlaybackState(state) }, 100)
            return
        }
        when (state) {
            is PlaybackState.Stopped -> {
                textViewGeneralInfo.text = getString(R.string.status_stopped)
                textViewStationName.visibility = View.GONE
                buttonPlay.setImageResource(R.drawable.ic_play_white)
                isPlaying = false
                stationAdapter?.setPlayingStation(null)
            }
            is PlaybackState.Buffering -> {
                textViewGeneralInfo.text = getString(R.string.buffering)
                textViewStationName.visibility = View.VISIBLE
                textViewStationName.text = currentStation?.name ?: ""
                buttonPlay.setImageResource(R.drawable.ic_pause_white)
                isPlaying = true
                stationAdapter?.setPlayingStation(currentStation)
            }
            is PlaybackState.Playing -> {
                textViewStationName.visibility = View.VISIBLE
                textViewStationName.text = state.stationName
                buttonPlay.setImageResource(R.drawable.ic_pause_white)
                isPlaying = true
                // 只有当当前信息是停止或缓冲状态时，才设置为电台名
                // 歌曲信息将通过 updateMetadata 更新
                if (textViewGeneralInfo.text == getString(R.string.status_stopped) ||
                    textViewGeneralInfo.text == getString(R.string.buffering)) {
                    // 不设置为电台名，保持为空或等待元数据
                }
                val playingStation = stationsList?.find { it.name == state.stationName }
                stationAdapter?.setPlayingStation(playingStation)
            }
            is PlaybackState.Paused -> {
                textViewStationName.visibility = View.VISIBLE
                textViewStationName.text = currentStation?.name ?: ""
                buttonPlay.setImageResource(R.drawable.ic_play_white)
                isPlaying = false
                stationAdapter?.setPlayingStation(null)
            }
            is PlaybackState.Error -> {
                textViewGeneralInfo.text = getString(R.string.status_error, state.message)
                buttonPlay.setImageResource(R.drawable.ic_play_white)
                isPlaying = false
                stationAdapter?.setPlayingStation(null)
            }
        }
    }

    fun updateMetadata(title: String?) {
        if (!isViewReady) return
        if (!title.isNullOrEmpty()) {
            textViewGeneralInfo.text = title
        }
    }

    private fun togglePlayPause() {
        playerManager?.let { manager ->
            if (manager.isPlaying()) {
                manager.pause()
            } else {
                currentStation?.let { station ->
                    manager.playStation(station)
                }
            }
        }
    }

    companion object {
        fun newInstance() = PlayerFullscreenFragment()
    }
}
