package com.example.ijkradio.utils

import android.content.Context
import com.example.ijkradio.data.Station
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonStationLoader {
    fun loadFromAssets(context: Context, fileName: String = "stations.json"): List<Station> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = inputStream.bufferedReader()
            val type = object : TypeToken<List<Station>>() {}.type
            val stations: List<Station> = Gson().fromJson(reader, type)
            reader.close()
            stations
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}