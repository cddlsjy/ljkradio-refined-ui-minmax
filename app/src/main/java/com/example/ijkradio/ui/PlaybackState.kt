package com.example.ijkradio.ui

/**
 * 播放状态密封类
 * 使用 Kotlin 密封类来表示播放器可能的各种状态
 */
sealed class PlaybackState {
    /**
     * 播放器停止状态
     */
    object Stopped : PlaybackState()

    /**
     * 正在缓冲状态
     */
    object Buffering : PlaybackState()

    /**
     * 正在播放状态
     * @param stationName 当前播放的电台名称
     */
    data class Playing(val stationName: String) : PlaybackState()

    /**
     * 暂停状态
     */
    object Paused : PlaybackState()

    /**
     * 错误状态
     * @param message 错误信息
     */
    data class Error(val message: String) : PlaybackState()
}
