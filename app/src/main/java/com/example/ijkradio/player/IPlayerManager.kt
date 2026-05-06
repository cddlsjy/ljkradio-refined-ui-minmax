package com.example.ijkradio.player

import com.example.ijkradio.data.Station
import com.example.ijkradio.ui.PlaybackState
import kotlinx.coroutines.flow.Flow

interface IPlayerManager {
    
    fun initialize()
    
    fun release()
    
    fun playStation(station: Station)
    
    fun pause()
    
    fun resume()
    
    fun stop()
    
    fun setVolume(volume: Float)
    
    fun setHardwareDecode(useHardware: Boolean)
    
    fun isPlaying(): Boolean
    
    fun getCurrentStation(): Station?
    
    val playbackState: Flow<PlaybackState>
}