package com.example.ijkradio

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.ijkradio.data.Station
import com.example.ijkradio.data.StationStorage
import com.example.ijkradio.player.IPlayerManager
import com.example.ijkradio.player.ExoPlayerManager
import com.example.ijkradio.player.IjkPlayerManager
import com.example.ijkradio.ui.PlaybackState
import com.example.ijkradio.ui.StationAdapter
import com.example.ijkradio.ui.PlayerFullscreenFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import android.view.KeyEvent
import android.view.WindowManager

/**
 * 主界面Activity
 * 管理电台列表、播放器控制和UI交互
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // UI组件
    private lateinit var recyclerView: RecyclerView
    private lateinit var playPauseButton: FloatingActionButton
    private lateinit var settingsButton: ImageButton
    private lateinit var statusTextView: TextView
    private lateinit var songTitleTextView: TextView
    private lateinit var emptyView: TextView
    private lateinit var volumeSlider: Slider
    private lateinit var volumeIcon: ImageView

    // 适配器
    private lateinit var stationAdapter: StationAdapter

    // 数据和播放器
    private lateinit var stationStorage: StationStorage
    private lateinit var playerManager: IPlayerManager

    // 电台列表
    private var stations: MutableList<Station> = mutableListOf()
    private var selectedStation: Station? = null
    private var autoPlayEnabled = true
    private var autoPlayLastStationEnabled = true
    private var pendingAutoPause = false

    private var isFullscreenMode = false
    private var fullscreenFragment: PlayerFullscreenFragment? = null

    private val m3uFilePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { parseM3UFile(it) }
    }

    private val m3uFileSaverLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri: Uri? ->
        uri?.let { exportM3UFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: Initializing activity")

        // 初始化组件
        initStorage()
        initViews()
        initPlayer(stationStorage.getUseExoPlayer())
        initRecyclerView()
        setupListeners()

        // 加载电台列表
        loadStations()

        // 恢复上次播放
        restoreLastPlayed()

        // 检查是否需要自动进入全屏播放模式
        if (stationStorage.getAutoFullscreenOnStart() && stationStorage.getLastPlayed() != null) {
            handler.postDelayed({
                enterFullscreenMode()
            }, 300)
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * 初始化UI组件
     */
    private fun initViews() {
        recyclerView = findViewById(R.id.stations_recycler_view)
        playPauseButton = findViewById(R.id.play_pause_button)
        settingsButton = findViewById(R.id.settings_button)
        statusTextView = findViewById(R.id.status_text_view)
        songTitleTextView = findViewById(R.id.song_title_text_view)
        emptyView = findViewById(R.id.empty_view)

        // 播放控制栏点击事件
        findViewById<LinearLayout>(R.id.playback_controls)?.setOnClickListener {
            if (!isFullscreenMode && selectedStation != null) {
                enterFullscreenMode()
            }
        }
    }

    /**
     * 初始化存储
     */
    private fun initStorage() {
        stationStorage = StationStorage(this)
    }

    /**
     * 初始化播放器
     */
    private fun initPlayer(useExoPlayer: Boolean) {
        playerManager = if (useExoPlayer) {
            ExoPlayerManager.getInstance(this)
        } else {
            IjkPlayerManager.getInstance(this)
        }
        playerManager.initialize()
        playerManager.setVolume(stationStorage.getVolume())
        // 应用保存的硬解码设置
        playerManager.setHardwareDecode(stationStorage.getUseHardwareDecode())
    }

    /**
     * 初始化RecyclerView
     */
    private fun initRecyclerView() {
        stationAdapter = StationAdapter(
            onStationClick = { station -> onStationClicked(station) },
            onDeleteClick = { station -> showDeleteDialog(station) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = stationAdapter
            setHasFixedSize(true)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        // 添加左滑删除功能
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val station = stationAdapter.getItemAt(position)
                if (station != null) {
                    showDeleteDialog(station)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 播放/暂停按钮
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        // 添加电台按钮
        findViewById<ImageButton>(R.id.add_station_button).setOnClickListener {
            showAddStationDialog()
        }

        // 设置按钮
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        // 监听播放器状态
        lifecycleScope.launch {
            playerManager.playbackState.collect {
                updatePlaybackUI(it)
            }
        }

        // 监听歌曲元数据
        lifecycleScope.launch {
            if (playerManager is ExoPlayerManager) {
                (playerManager as ExoPlayerManager).metadataFlow.collect {
                    songTitleTextView.text = it
                    songTitleTextView.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * 加载电台列表
     */
    private fun loadStations() {
        stations = stationStorage.getStations().toMutableList()
        stationAdapter.submitList(stations.toList())
        updateEmptyView()
    }

    /**
     * 恢复上次播放
     */
    private fun restoreLastPlayed() {
        val lastPlayed = stationStorage.getLastPlayed()
        if (lastPlayed != null) {
            selectedStation = lastPlayed
            stationAdapter.setSelectedStation(lastPlayed)
            // 如果启用了自动播放上一次电台，则自动播放
            if (autoPlayLastStationEnabled) {
                playerManager.playStation(lastPlayed)
            }
        }
    }

    /**
     * 切换播放器引擎
     */
    private fun switchPlayerEngine(useExoPlayer: Boolean) {
        val wasPlaying = playerManager.isPlaying()
        val currentStation = playerManager.getCurrentStation()
        val wasPaused = !wasPlaying && currentStation != null

        // 释放旧播放器
        playerManager.release()

        // 创建新播放器
        initPlayer(useExoPlayer)

        // 重新订阅播放状态（因为 playerManager 实例已更新）
        lifecycleScope.launch {
            playerManager.playbackState.collect {
                updatePlaybackUI(it)
            }
        }

        // 重新订阅元数据（因为 playerManager 实例已更新）
        lifecycleScope.launch {
            if (playerManager is ExoPlayerManager) {
                (playerManager as ExoPlayerManager).metadataFlow.collect {
                    songTitleTextView.text = it
                    songTitleTextView.visibility = View.VISIBLE
                }
            }
        }

        // 恢复播放状态
        when {
            wasPlaying && currentStation != null -> {
                playerManager.playStation(currentStation)
            }
            wasPaused && currentStation != null -> {
                // 设置标志位，待播放器准备好后自动暂停（无声恢复）
                pendingAutoPause = true
                playerManager.playStation(currentStation)
            }
        }

        // 更新 UI 选中状态
        if (currentStation != null) {
            selectedStation = currentStation
            stationAdapter.setSelectedStation(currentStation)
        }

        Toast.makeText(
            this,
            "播放器引擎已切换为 ${if (useExoPlayer) "ExoPlayer" else "IjkPlayer"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 电台点击事件
     */
    private fun onStationClicked(station: Station) {
        Log.d(TAG, "Station clicked: ${station.name}")
        selectedStation = station
        stationAdapter.setSelectedStation(station)
        stationStorage.saveLastPlayed(station)

        // 如果当前正在播放其他电台，切换到新电台
        val currentStation = playerManager.getCurrentStation()
        if (currentStation?.id != station.id) {
            playerManager.playStation(station)
        }
    }

    /**
     * 切换播放/暂停
     */
    private fun togglePlayPause() {
        val currentStation = selectedStation ?: return

        when {
            playerManager.isPlaying() -> {
                playerManager.pause()
            }
            playerManager.getCurrentStation()?.id == currentStation.id -> {
                playerManager.resume()
            }
            else -> {
                playerManager.playStation(currentStation)
            }
        }
    }

    /**
     * 更新播放UI
     */
    private fun updatePlaybackUI(state: PlaybackState) {
        when (state) {
            is PlaybackState.Stopped -> {
                statusTextView.text = getString(R.string.status_stopped)
                playPauseButton.setImageResource(R.drawable.ic_play)
                stationAdapter.setPlayingStation(null)
                songTitleTextView.visibility = View.GONE
            }
            is PlaybackState.Buffering -> {
                statusTextView.text = getString(R.string.status_buffering)
                playPauseButton.setImageResource(R.drawable.ic_pause)
            }
            is PlaybackState.Playing -> {
                statusTextView.text = state.stationName
                playPauseButton.setImageResource(R.drawable.ic_pause)
                val station = stations.find { it.name == state.stationName }
                stationAdapter.setPlayingStation(station)

                // 如果处于待自动暂停状态，立即暂停（无声恢复）
                if (pendingAutoPause) {
                    pendingAutoPause = false
                    playerManager.pause()
                }
            }
            is PlaybackState.Paused -> {
                statusTextView.text = getString(R.string.status_paused)
                playPauseButton.setImageResource(R.drawable.ic_play)
            }
            is PlaybackState.Error -> {
                statusTextView.text = getString(R.string.status_error, state.message)
                playPauseButton.setImageResource(R.drawable.ic_play)
                songTitleTextView.visibility = View.GONE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 显示添加电台对话框
     */
    private fun showAddStationDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_station, null)

        val nameInput = dialogView.findViewById<EditText>(R.id.station_name_input)
        val urlInput = dialogView.findViewById<EditText>(R.id.station_url_input)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.station_description_input)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_station_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_add) { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()

                if (name.isNotEmpty() && url.isNotEmpty()) {
                    val station = Station(
                        name = name,
                        url = if (url.startsWith("http")) url else "http://$url",
                        description = description
                    )
                    addStation(station)
                } else {
                    Toast.makeText(this, R.string.error_invalid_input, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteDialog(station: Station) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, station.name))
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                deleteStation(station)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * 添加电台
     */
    private fun addStation(station: Station) {
        if (station.isValid()) {
            stationStorage.addStation(station)
            loadStations()
            Toast.makeText(this, R.string.station_added, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.error_invalid_station, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 删除电台
     */
    private fun deleteStation(station: Station) {
        // 如果删除的是当前播放的电台，停止播放
        if (playerManager.getCurrentStation()?.id == station.id) {
            playerManager.stop()
        }

        // 如果删除的是选中的电台，清除选中
        if (selectedStation?.id == station.id) {
            selectedStation = null
        }

        stationStorage.removeStation(station)
        loadStations()
        Toast.makeText(this, R.string.station_deleted, Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新空状态视图
     */
    private fun updateEmptyView() {
        if (stations.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    // ==================== 生命周期管理 ====================

    override fun onPause() {
        super.onPause()
        // 保存当前状态
        playerManager.getCurrentStation()?.let {
            stationStorage.saveLastPlayed(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放播放器资源
        if (::playerManager.isInitialized) {
            playerManager.release()
        }
    }

    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_settings, null)

        volumeSlider = dialogView.findViewById<Slider>(R.id.volume_slider)
        volumeIcon = dialogView.findViewById<ImageView>(R.id.volume_icon)
        val radioExo = dialogView.findViewById<RadioButton>(R.id.radio_exo)
        val radioIjk = dialogView.findViewById<RadioButton>(R.id.radio_ijk)
        val radioHardware = dialogView.findViewById<RadioButton>(R.id.radio_hardware)
        val radioSoftware = dialogView.findViewById<RadioButton>(R.id.radio_software)
        val autoPlaySwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.auto_play_switch)
        val autoPlayLastStationSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.auto_play_last_station_switch)
        val autoFullscreenSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.auto_fullscreen_switch)
        val importM3uButton = dialogView.findViewById<Button>(R.id.button_import_m3u)
        val exportM3uButton = dialogView.findViewById<Button>(R.id.button_export_m3u)

        // 初始化音量滑块
        volumeSlider.value = stationStorage.getVolume()
        updateVolumeIcon(stationStorage.getVolume())

        // 初始化播放器引擎
        val useExoPlayer = stationStorage.getUseExoPlayer()
        if (useExoPlayer) {
            radioExo.isChecked = true
        } else {
            radioIjk.isChecked = true
        }

        // 初始化解码方式，默认打开软解码
        radioSoftware.isChecked = true
        playerManager.setHardwareDecode(false)

        // 初始化自动播放开关
        autoPlaySwitch.isChecked = autoPlayEnabled
        
        // 初始化自动播放上一次电台开关
        autoPlayLastStationSwitch.isChecked = autoPlayLastStationEnabled

        // 初始化自动全屏播放开关
        autoFullscreenSwitch.isChecked = stationStorage.getAutoFullscreenOnStart()

        // 音量滑块监听器
        volumeSlider.addOnChangeListener { slider: Slider, value: Float, fromUser: Boolean ->
            if (fromUser) {
                playerManager.setVolume(value)
                stationStorage.saveVolume(value)
                updateVolumeIcon(value)
            }
        }

        // 自动播放开关监听器
        autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            autoPlayEnabled = isChecked
        }

        // 自动播放上一次电台开关监听器
        autoPlayLastStationSwitch.setOnCheckedChangeListener { _, isChecked ->
            autoPlayLastStationEnabled = isChecked
        }

        // 自动全屏播放开关监听器
        autoFullscreenSwitch.setOnCheckedChangeListener { _, isChecked ->
            stationStorage.saveAutoFullscreenOnStart(isChecked)
        }

        // 导入M3U播放列表按钮监听器
        importM3uButton.setOnClickListener {
            m3uFilePickerLauncher.launch("*/*")
        }

        // 导出M3U播放列表按钮监听器
        exportM3uButton.setOnClickListener {
            exportStationsToM3U()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val useExoPlayerNew = radioExo.isChecked
                val oldUseExoPlayer = stationStorage.getUseExoPlayer()
                val useHardwareDecode = radioHardware.isChecked

                // 保存音量
                val volume = volumeSlider.value
                playerManager.setVolume(volume)
                stationStorage.saveVolume(volume)

                // 保存自动播放开关状态（成员变量已在 Switch 监听器中更新，无需额外保存）

                // 保存播放器引擎设置
                stationStorage.saveUseExoPlayer(useExoPlayerNew)

                // 保存硬解码设置
                stationStorage.saveUseHardwareDecode(useHardwareDecode)

                // 如果引擎发生变化，切换播放器
                if (useExoPlayerNew != oldUseExoPlayer) {
                    switchPlayerEngine(useExoPlayerNew)
                } else {
                    // 引擎未变，仅应用解码方式
                    playerManager.setHardwareDecode(useHardwareDecode)
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        // 设置按钮颜色
        dialog.setOnShowListener { 
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.colorPrimary))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.colorPrimary))
        }
        
        dialog.show()
    }

    /**
     * 解析M3U文件
     */
    private fun parseM3UFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val stations = mutableListOf<Station>()
                val lines = inputStream.bufferedReader().readLines()

                var currentName = ""
                var currentLogoUrl = ""

                for (line in lines) {
                    val trimmedLine = line.trim()

                    when {
                        trimmedLine.startsWith("#EXTINF:") -> {
                            // 提取电台名称
                            val nameStart = trimmedLine.lastIndexOf(',')
                            if (nameStart != -1 && nameStart < trimmedLine.length - 1) {
                                currentName = trimmedLine.substring(nameStart + 1).trim()
                            }
                        }
                        trimmedLine.startsWith("#EXTIMG:") -> {
                            // 提取电台图标URL
                            currentLogoUrl = trimmedLine.substring("#EXTIMG:".length).trim()
                        }
                        trimmedLine.startsWith("#") -> {
                            // 跳过其他注释行
                        }
                        trimmedLine.isNotEmpty() -> {
                            // 这是一个电台URL
                            val url = trimmedLine
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                val station = Station(
                                    name = if (currentName.isNotEmpty()) currentName else "未知电台",
                                    url = url,
                                    description = "",
                                    logoUrl = currentLogoUrl
                                )
                                stations.add(station)
                            }
                            // 重置当前电台信息
                            currentName = ""
                            currentLogoUrl = ""
                        }
                    }
                }

                if (stations.isNotEmpty()) {
                    // 添加所有解析到的电台
                    var addedCount = 0
                    stations.forEach { station ->
                        if (station.isValid()) {
                            stationStorage.addStation(station)
                            addedCount++
                        }
                    }

                    if (addedCount > 0) {
                        loadStations()
                        Toast.makeText(
                            this,
                            getString(R.string.import_m3u_success, addedCount),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.import_m3u_no_valid),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.import_m3u_no_valid),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析M3U文件失败", e)
            Toast.makeText(
                this,
                getString(R.string.import_m3u_failed, e.message ?: "未知错误"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 导出电台列表到M3U文件
     */
    private fun exportStationsToM3U() {
        try {
            val stations = stationStorage.getStations()
            if (stations.isEmpty()) {
                Toast.makeText(
                    this,
                    "当前没有可导出的电台",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val fileName = "radio_stations_${System.currentTimeMillis()}.m3u"
            m3uFileSaverLauncher.launch(fileName)
        } catch (e: Exception) {
            Log.e(TAG, "启动文件保存失败", e)
            Toast.makeText(
                this,
                getString(R.string.export_m3u_failed, e.message ?: "未知错误"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 写入M3U内容到文件
     */
    private fun exportM3UFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = outputStream.bufferedWriter()
                val stations = stationStorage.getStations()

                writer.write("#EXTM3U\n")

                stations.forEach { station ->
                    // 写入电台信息
                    writer.write("#EXTINF:-1,${station.name}\n")
                    if (station.logoUrl.isNotEmpty()) {
                        writer.write("#EXTIMG:${station.logoUrl}\n")
                    }
                    writer.write("${station.url}\n")
                }

                writer.flush()
            }

            Toast.makeText(
                this,
                getString(R.string.export_m3u_success, stationStorage.getStations().size),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "写入M3U文件失败", e)
            Toast.makeText(
                this,
                getString(R.string.export_m3u_failed, e.message ?: "未知错误"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 更新音量图标
     */
    private fun updateVolumeIcon(volume: Float) {
        val iconRes = when {
            volume <= 0f -> R.drawable.ic_volume_off
            volume < 0.5f -> R.drawable.ic_volume_down
            else -> R.drawable.ic_volume_up
        }
        volumeIcon.setImageResource(iconRes)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isFullscreenMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    playPreviousStation()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    playNextStation()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    fullscreenFragment?.let { fragment ->
                        playerManager.let { manager ->
                            if (manager.isPlaying()) {
                                manager.pause()
                            } else {
                                selectedStation?.let { station ->
                                    manager.playStation(station)
                                }
                            }
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    toggleMode()
                    return true
                }
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    toggleMode()
                    return true
                }
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveSelection(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveSelection(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                selectedStation?.let {
                    if (playerManager.isPlaying() && playerManager.getCurrentStation()?.id == it.id) {
                        playerManager.pause()
                    } else {
                        playStationAndUpdateUI(it)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun moveSelection(delta: Int) {
        val count = stationAdapter.itemCount
        if (count == 0) return

        // 获取当前选中位置，若无选中则根据方向决定起始位置
        val currentPos = if (selectedStation == null) {
            if (delta > 0) -1 else count  // 使下方模运算得到正确边界
        } else {
            stations.indexOfFirst { it.id == selectedStation?.id }.coerceAtLeast(0)
        }

        // 循环计算新位置
        val newPos = ((currentPos + delta) % count + count) % count
        val station = stationAdapter.getItemAt(newPos)
        stationAdapter.setSelectedStation(station)
        recyclerView.smoothScrollToPosition(newPos)
        if (autoPlayEnabled && station != null) {
            playStationAndUpdateUI(station)
        }
    }

    private fun getSelectedPosition(): Int {
        val selected = stationAdapter.getSelectedStation() ?: return 0
        return stations.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
    }

    fun playPreviousStation() {
        val count = stationAdapter.itemCount
        if (count == 0) return

        val currentPos = stations.indexOfFirst { it.id == selectedStation?.id }.coerceAtLeast(0)
        val newPos = ((currentPos - 1) % count + count) % count
        val station = stationAdapter.getItemAt(newPos)
        if (station != null) {
            stationAdapter.setSelectedStation(station)
            playStationAndUpdateUI(station)
        }
    }

    fun playNextStation() {
        val count = stationAdapter.itemCount
        if (count == 0) return

        val currentPos = stations.indexOfFirst { it.id == selectedStation?.id }.coerceAtLeast(0)
        val newPos = (currentPos + 1) % count
        val station = stationAdapter.getItemAt(newPos)
        if (station != null) {
            stationAdapter.setSelectedStation(station)
            playStationAndUpdateUI(station)
        }
    }

    private fun playStationAndUpdateUI(station: Station) {
        onStationClicked(station)
    }

    private fun toggleMode() {
        if (isFullscreenMode) {
            exitFullscreenMode()
        } else {
            enterFullscreenMode()
        }
    }

    override fun onBackPressed() {
        if (isFullscreenMode) {
            exitFullscreenMode()
        } else {
            moveTaskToBack(true)
            super.onBackPressed()
        }
    }

    private fun enterFullscreenMode() {
        if (isFullscreenMode) return

        isFullscreenMode = true

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        fullscreenFragment = PlayerFullscreenFragment.newInstance().apply {
            setPlayerFullscreenListener(object : PlayerFullscreenFragment.PlayerFullscreenListener {
                override fun onExitFullscreen() {
                    exitFullscreenMode()
                }
            })
            setPlayerManager(playerManager)
        }

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fullscreenFragment!!)
            .commit()

        lifecycleScope.launch {
            playerManager.playbackState.collect {
                fullscreenFragment?.updatePlaybackState(it)
            }
        }

        lifecycleScope.launch {
            if (playerManager is ExoPlayerManager) {
                (playerManager as ExoPlayerManager).metadataFlow.collect {
                    fullscreenFragment?.updateMetadata(it)
                }
            }
        }

        fullscreenFragment?.updateStationInfo(selectedStation)
    }

    private fun exitFullscreenMode() {
        if (!isFullscreenMode) return

        isFullscreenMode = false

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        fullscreenFragment?.let { fragment ->
            supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commit()
        }
        fullscreenFragment = null

        recyclerView.requestFocus()
    }
}
