package com.example.ijkradio.data

import android.content.Context
import android.content.SharedPreferences

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 电台存储管理器
 * 使用 SharedPreferences 存储电台列表和播放状态
 */
class StationStorage(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "ijk_radio_prefs"
        private const val KEY_STATIONS = "stations"
        private const val KEY_LAST_PLAYED_ID = "last_played_id"
        private const val KEY_LAST_VOLUME = "last_volume"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_USE_EXO_PLAYER = "use_exo_player"
        private const val KEY_USE_HARDWARE_DECODE = "use_hardware_decode"
        private const val KEY_AUTO_FULLSCREEN_ON_START = "auto_fullscreen_on_start"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * 保存电台列表
     */
    fun saveStations(stations: List<Station>) {
        val json = gson.toJson(stations)
        prefs.edit().putString(KEY_STATIONS, json).apply()
    }

    /**
     * 获取电台列表
     */
    fun getStations(): List<Station> {
        val json = prefs.getString(KEY_STATIONS, null)
        val stations = if (json != null) {
            try {
                val type = object : TypeToken<List<Station>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            // 首次运行：使用默认电台列表
            val defaultStations = getDefaultStations()
            saveStations(defaultStations)
            defaultStations
        }
        return stations
    }

    /**
     * 添加电台
     */
    fun addStation(station: Station) {
        val stations = getStations().toMutableList()
        // 检查是否已存在同名电台
        if (stations.none { it.name == station.name && it.url == station.url }) {
            stations.add(station)
            saveStations(stations)
        }
    }

    /**
     * 删除电台
     */
    fun removeStation(station: Station) {
        val stations = getStations().toMutableList()
        val iterator = stations.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == station.id) {
                iterator.remove()
                break
            }
        }
        saveStations(stations)
    }

    /**
     * 删除电台 by ID
     */
    fun removeStationById(stationId: String) {
        val stations = getStations().toMutableList()
        val iterator = stations.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == stationId) {
                iterator.remove()
                break
            }
        }
        saveStations(stations)
    }

    /**
     * 更新电台
     */
    fun updateStation(station: Station) {
        val stations = getStations().toMutableList()
        val index = stations.indexOfFirst { it.id == station.id }
        if (index != -1) {
            stations[index] = station
            saveStations(stations)
        }
    }

    /**
     * 保存上次播放的电台ID
     */
    fun saveLastPlayed(station: Station) {
        prefs.edit()
            .putString(KEY_LAST_PLAYED_ID, station.id)
            .apply()
    }

    /**
     * 获取上次播放的电台ID
     */
    fun getLastPlayedId(): String? {
        return prefs.getString(KEY_LAST_PLAYED_ID, null)
    }

    /**
     * 获取上次播放的电台
     */
    fun getLastPlayed(): Station? {
        val lastId = getLastPlayedId() ?: return null
        return getStations().find { it.id == lastId }
    }

    /**
     * 保存音量
     */
    fun saveVolume(volume: Float) {
        prefs.edit().putFloat(KEY_LAST_VOLUME, volume).apply()
    }

    /**
     * 获取音量
     */
    fun getVolume(): Float {
        return prefs.getFloat(KEY_LAST_VOLUME, 1.0f)
    }

    /**
     * 保存播放位置（毫秒）
     */
    fun savePosition(position: Long) {
        prefs.edit().putLong(KEY_LAST_POSITION, position).apply()
    }

    /**
     * 获取播放位置（毫秒）
     */
    fun getPosition(): Long {
        return prefs.getLong(KEY_LAST_POSITION, 0L)
    }

    /**
     * 清空所有数据
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * 获取默认电台列表
     */
    private fun getDefaultStations(): List<Station> {
        return listOf(
            Station(
                name = "湖南交通广播",
                url = "http://a.live.hnradio.com/jtpd/radio120k_jtpd.m3u8?auth_key=1588751155-0-0-301d7e28868eff70a72edf5e4569b546",
                description = "湖南交通广播"
            ),
            Station(
                name = "CNR-1-中国之声",
                url = "http://ngcdn001.cnr.cn/live/zgzs/index.m3u8",
                description = "CNR-1-中国之声"
            ),
            Station(
                name = "CNR-15 中国交通广播",
                url = "https://ngcdn002.cnr.cn/live/gsgljtgb/index.m3u8",
                description = "CNR-15 中国交通广播"
            ),
            Station(
                name = "CMG环球资讯广播",
                url = "https://sk.cri.cn/905.m3u8",
                description = "CMG环球资讯广播"
            ),
            Station(
                name = "CNR-2 经济之声",
                url = "http://ngcdn002.cnr.cn/live/jjzs/index.m3u8",
                description = "CNR-2 经济之声"
            ),
            Station(
                name = "AsiaFM高清音乐台",
                url = "http://asiafm.hk:8000/asiahd",
                description = "AsiaFM高清音乐台"
            ),
            Station(
                name = "AsiaFM 亚洲热歌台【2023.10.17】",
                url = "http://hot.asiafm.net:8000/asiafm",
                description = "AsiaFM 亚洲热歌台"
            ),
            Station(
                name = "AsiaFM亚洲经典台",
                url = "http://goldfm.cn:8000/goldfm",
                description = "AsiaFM亚洲经典台"
            ),
            Station(
                name = "AsiaFM 亚洲音乐台【2023.10.18】",
                url = "http://asiafm.hk:8000/asiafm",
                description = "AsiaFM 亚洲音乐台"
            ),
            Station(
                name = "深圳交通广播",
                url = "http://lhttp.qingting.fm/live/1272/64k.mp3",
                description = "深圳交通广播"
            ),
            Station(
                name = "上海交通广播",
                url = "http://lhttp.qingting.fm/live/266/64k.mp3",
                description = "上海交通广播"
            ),
            Station(
                name = "青岛交通广播",
                url = "http://lhttp.qingting.fm/live/1676/64k.mp3",
                description = "青岛交通广播"
            ),
            Station(
                name = "北京交通广播",
                url = "https://lhttp.qingting.fm/live/336/64k.mp3",
                description = "北京交通广播"
            ),
            Station(
                name = "常德交通广播",
                url = "https://lhttp.qingting.fm/live/15318209/64k.mp3",
                description = "常德交通广播"
            )
        )
    }

    /**
     * 检查是否为首次运行
     */
    fun isFirstRun(): Boolean {
        return !prefs.contains(KEY_STATIONS)
    }

    /**
     * 标记首次运行已完成
     */
    fun markFirstRunComplete() {
        // 首次运行后会保存电台数据，所以只需检查KEY_STATIONS即可
    }

    /**
     * 保存播放器引擎选择
     */
    fun saveUseExoPlayer(useExoPlayer: Boolean) {
        prefs.edit().putBoolean(KEY_USE_EXO_PLAYER, useExoPlayer).apply()
    }

    /**
     * 获取播放器引擎选择
     */
    fun getUseExoPlayer(): Boolean {
        return prefs.getBoolean(KEY_USE_EXO_PLAYER, true) // 默认使用ExoPlayer
    }

    /**
     * 保存硬解码设置
     */
    fun saveUseHardwareDecode(useHardware: Boolean) {
        prefs.edit().putBoolean(KEY_USE_HARDWARE_DECODE, useHardware).apply()
    }

    /**
     * 获取硬解码设置（默认 true）
     */
    fun getUseHardwareDecode(): Boolean {
        return prefs.getBoolean(KEY_USE_HARDWARE_DECODE, true)
    }

    /**
     * 保存启动时自动进入全屏播放模式设置
     */
    fun saveAutoFullscreenOnStart(autoFullscreen: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FULLSCREEN_ON_START, autoFullscreen).apply()
    }

    /**
     * 获取启动时自动进入全屏播放模式设置（默认 false）
     */
    fun getAutoFullscreenOnStart(): Boolean {
        return prefs.getBoolean(KEY_AUTO_FULLSCREEN_ON_START, false)
    }
}
