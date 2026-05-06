package com.example.ijkradio.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build

/**
 * 网络状态监听器
 * 用于检测网络连接状态变化
 * 兼容API 19+，使用BroadcastReceiver替代NetworkCallback
 */
class NetworkHelper(private val context: Context) {

    /**
     * 网络状态监听接口
     */
    interface NetworkStateListener {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }

    private var networkStateListener: NetworkStateListener? = null
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkReceiver: BroadcastReceiver? = null
    private var isRegistered = false

    /**
     * 设置网络状态监听器
     */
    fun setNetworkStateListener(listener: NetworkStateListener) {
        networkStateListener = listener
    }

    /**
     * 开始监听网络状态
     */
    fun startListening() {
        // 创建广播接收器
        networkReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                    val wasConnected = isNetworkAvailable()
                    // 延迟检查以确保网络状态已更新
                    android.os.Handler().postDelayed({
                        val isConnected = isNetworkAvailable()
                        if (isConnected && !wasConnected) {
                            networkStateListener?.onNetworkAvailable()
                        } else if (!isConnected && wasConnected) {
                            networkStateListener?.onNetworkLost()
                        }
                    }, 500)
                }
            }
        }

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiver(networkReceiver, filter)
        isRegistered = true
    }

    /**
     * 停止监听网络状态
     */
    fun stopListening() {
        if (isRegistered && networkReceiver != null) {
            try {
                context.unregisterReceiver(networkReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered
            }
            isRegistered = false
        }
        networkReceiver = null
        networkStateListener = null
    }

    /**
     * 检查是否有网络连接
     */
    fun isNetworkAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected ?: false
        }
    }

    /**
     * 检查是否通过移动网络连接
     */
    fun isMobileNetwork(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }

    /**
     * 检查是否通过WiFi连接
     */
    fun isWifiNetwork(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    /**
     * 获取当前网络类型描述
     */
    fun getNetworkTypeDescription(): String {
        return when {
            isWifiNetwork() -> "WiFi"
            isMobileNetwork() -> "移动网络"
            isNetworkAvailable() -> "其他网络"
            else -> "无网络"
        }
    }
}
