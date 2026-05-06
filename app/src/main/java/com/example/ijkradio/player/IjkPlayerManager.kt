package com.example.ijkradio.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.example.ijkradio.data.Station
import com.example.ijkradio.ui.PlaybackState
import kotlinx.coroutines.flow.Flow
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.misc.ITrackInfo

/**
 * IjkPlayer 播放器管理器单例类
 * 负责管理 IjkMediaPlayer 的生命周期和播放控制
 */
class IjkPlayerManager private constructor(private val context: Context) : IPlayerManager {

    companion object {
        private const val TAG = "IjkPlayerManager"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer
        private const val RECONNECT_ATTEMPTS = 5

        @Volatile
        private var instance: IjkPlayerManager? = null

        fun getInstance(context: Context): IjkPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: IjkPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // 播放器实例
    private var ijkPlayer: IjkMediaPlayer? = null

    // 当前播放的电台
    private var currentStation: Station? = null

    // 播放状态
    private val _playbackState = MutableLiveData<PlaybackState>(PlaybackState.Stopped)
    val playbackStateLiveData: LiveData<PlaybackState> = _playbackState
    
    // 实现IPlayerManager接口的playbackState属性
    override val playbackState: Flow<PlaybackState>
        get() = _playbackState.asFlow()

    // 播放进度（用于音频可视化）
    private val _playbackPosition = MutableLiveData<Long>(0L)
    val playbackPosition: LiveData<Long> = _playbackPosition

    // Handler 用于定时更新
    private val handler = Handler(Looper.getMainLooper())
    private var positionUpdateRunnable: Runnable? = null

    // 是否已初始化
    private var isInitialized = false

    // 音量 (0.0 - 1.0)
    private var currentVolume = 1.0f

    // 硬解码开关
    private var hardwareDecodeEnabled = true

    /**
     * 初始化播放器
     */
    override fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "Player already initialized")
            return
        }

        try {
            // 加载 IjkPlayer 原生库
            IjkMediaPlayer.loadLibrariesOnce(null)

            // 创建播放器实例
            ijkPlayer = IjkMediaPlayer().apply {
                configurePlayer()
                setupListeners()
            }

            isInitialized = true
            Log.d(TAG, "Player initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize player", e)
            _playbackState.postValue(PlaybackState.Error("播放器初始化失败: ${e.message}"))
        }
    }

    /**
     * 配置播放器参数
     */
    private fun IjkMediaPlayer.configurePlayer() {
        if (hardwareDecodeEnabled) {
            // 启用硬解码
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)
        } else {
            // 禁用硬解码，使用软解
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
        }

        // 音频输出使用 OpenSL ES
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1)

        // 缓冲大小（微秒），弱网时增加缓冲
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", BUFFER_SIZE.toLong())

        // 准备完成后自动开始播放
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)

        // 错误恢复尝试次数
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", RECONNECT_ATTEMPTS.toLong())

        // 启用快速定位（对于直播流很重要）
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fflags", "fastseek")

        // 设置超时时间（微秒）
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "timeout", 30000000L) // 30秒

        // 无限缓冲（直播流推荐）
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1)
    }

    /**
     * 设置播放器监听器
     */
    private fun IjkMediaPlayer.setupListeners() {
        setOnPreparedListener(OnPreparedListenerImpl())
        setOnErrorListener(OnErrorListenerImpl())
        setOnCompletionListener(OnCompletionListenerImpl())
        setOnInfoListener(OnInfoListenerImpl())
        setOnBufferingUpdateListener(OnBufferingUpdateListenerImpl())
    }

    /**
     * 播放电台
     */
    override fun playStation(station: Station) {
        Log.d(TAG, "Playing station: ${station.name}, URL: ${station.url}")
        currentStation = station
        _playbackState.postValue(PlaybackState.Buffering)

        try {
            ijkPlayer?.let { player ->
                player.reset()
                player.configurePlayer()
                player.setupListeners()

                // ========== HTTP 头设置（完整替换） ==========
                // 通用 User-Agent（模拟浏览器）
                player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

                // 关键：告诉服务器需要 ICY 元数据（Shoutcast/Icecast 流必需）
                player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "icy", 1)
                // 启用 ICY 元数据解析（可选，用于获取歌曲信息）
                player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "icy_metadata", 1)

                // 根据域名设置特定 Referer
                val referer = when {
                    station.url.contains("cri.cn") || station.url.contains("sk.cri.cn") -> "https://sk.cri.cn/"
                    station.url.contains("hnradio.com") -> "http://a.live.hnradio.com/"
                    station.url.contains("cnr.cn") -> "http://ngcdn001.cnr.cn/"
                    station.url.contains("asiafm.hk") || station.url.contains("asiafm.net") || station.url.contains("goldfm.cn") -> "http://asiafm.hk/"
                    station.url.contains("qingting.fm") -> "https://www.qingting.fm/"
                    else -> null
                }
                referer?.let {
                    player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "referer", it)
                }

                // 央广流可能需要保持长连接
                if (station.url.contains("cnr.cn")) {
                    player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "multiple_requests", 1)
                }
                // ================================================

                player.dataSource = station.url
                player.prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing station", e)
            _playbackState.postValue(PlaybackState.Error("播放失败: ${e.message}"))
        }
    }

    /**
     * 暂停播放
     */
    override fun pause() {
        try {
            ijkPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _playbackState.postValue(PlaybackState.Paused)
                    stopPositionUpdates()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    /**
     * 恢复播放
     */
    override fun resume() {
        try {
            ijkPlayer?.let { player ->
                player.start()
                currentStation?.let { station ->
                    _playbackState.postValue(PlaybackState.Playing(station.name))
                }
                startPositionUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    /**
     * 停止播放
     */
    override fun stop() {
        try {
            stopPositionUpdates()
            ijkPlayer?.stop()
            _playbackState.postValue(PlaybackState.Stopped)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * 设置音量
     * @param volume 音量值 (0.0 - 1.0)
     */
    override fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            ijkPlayer?.setVolume(currentVolume, currentVolume)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    /**
     * 获取当前音量
     */
    fun getVolume(): Float = currentVolume

    /**
     * 设置硬解码开关
     */
    override fun setHardwareDecode(enabled: Boolean) {
        hardwareDecodeEnabled = enabled
        if (isInitialized) {
            ijkPlayer?.let { player ->
                if (hardwareDecodeEnabled) {
                    player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
                } else {
                    player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
                }
            }
        }
    }

    /**
     * 获取硬解码开关状态
     */
    fun isHardwareDecodeEnabled(): Boolean {
        return hardwareDecodeEnabled
    }

    /**
     * 是否正在播放
     */
    override fun isPlaying(): Boolean {
        return ijkPlayer?.isPlaying == true
    }

    /**
     * 获取当前播放的电台
     */
    override fun getCurrentStation(): Station? = currentStation

    /**
     * 释放播放器资源
     */
    override fun release() {
        stopPositionUpdates()
        try {
            ijkPlayer?.release()
            ijkPlayer = null
            isInitialized = false
            currentStation = null
            _playbackState.postValue(PlaybackState.Stopped)
            Log.d(TAG, "Player released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player", e)
        }
    }

    /**
     * 开始位置更新
     */
    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateRunnable = object : Runnable {
            override fun run() {
                ijkPlayer?.let { player ->
                    try {
                        _playbackPosition.postValue(player.currentPosition)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting position", e)
                    }
                }
                handler.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        positionUpdateRunnable?.let { handler.post(it) }
    }

    /**
     * 停止位置更新
     */
    private fun stopPositionUpdates() {
        positionUpdateRunnable?.let { handler.removeCallbacks(it) }
        positionUpdateRunnable = null
    }

    // ==================== 内部监听器实现 ====================

    private inner class OnPreparedListenerImpl : IMediaPlayer.OnPreparedListener {
        override fun onPrepared(mp: IMediaPlayer?) {
            Log.d(TAG, "Media prepared")
            mp?.let { player ->
                player.setVolume(currentVolume, currentVolume)
                player.start()
                currentStation?.let { station ->
                    _playbackState.postValue(PlaybackState.Playing(station.name))
                }
                startPositionUpdates()
            }
        }
    }

    private inner class OnErrorListenerImpl : IMediaPlayer.OnErrorListener {
        override fun onError(mp: IMediaPlayer?, what: Int, extra: Int): Boolean {
            val errorMsg = when (what) {
                IMediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK ->
                    "视频内容无效"
                IMediaPlayer.MEDIA_ERROR_UNKNOWN ->
                    "未知错误: what=$what, extra=$extra"
                else -> "播放错误: what=$what, extra=$extra"
            }
            Log.e(TAG, "Playback error: $errorMsg")
            _playbackState.postValue(PlaybackState.Error(errorMsg))
            stopPositionUpdates()
            return true // 错误已处理
        }
    }

    private inner class OnCompletionListenerImpl : IMediaPlayer.OnCompletionListener {
        override fun onCompletion(mp: IMediaPlayer?) {
            Log.d(TAG, "Playback completed")
            _playbackState.postValue(PlaybackState.Stopped)
            stopPositionUpdates()
        }
    }

    private inner class OnInfoListenerImpl : IMediaPlayer.OnInfoListener {
        override fun onInfo(mp: IMediaPlayer?, what: Int, extra: Int): Boolean {
            when (what) {
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    Log.d(TAG, "Buffering started")
                    _playbackState.postValue(PlaybackState.Buffering)
                }
                IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    Log.d(TAG, "Buffering ended")
                    currentStation?.let { station ->
                        _playbackState.postValue(PlaybackState.Playing(station.name))
                    }
                }
                IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH -> {
                    Log.d(TAG, "Network bandwidth: $extra")
                }
                IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START -> {
                    Log.d(TAG, "Audio rendering started")
                }
            }
            return false
        }
    }

    private inner class OnBufferingUpdateListenerImpl : IMediaPlayer.OnBufferingUpdateListener {
        override fun onBufferingUpdate(mp: IMediaPlayer?, percent: Int) {
            if (percent in 1..99) {
                Log.d(TAG, "Buffering: $percent%")
            }
        }
    }
}
