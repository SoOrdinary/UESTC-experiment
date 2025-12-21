package com.soordinary.transfer.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.text.TextUtils
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Enumeration

/**
 * 网络通信工具类（适配Wi-Fi/移动流量/热点模式）
 */
object NetworkUtil {
    const val PORT: Int = 8888
    // 安卓热点默认网关IP（99%的设备都是这个网段）
    private const val HOTSPOT_DEFAULT_GATEWAY = "192.168.43.1"
    private const val HOTSPOT_ALTERNATE_GATEWAY = "192.168.1.1"

    /**
     * 检查网络是否有效（兼容热点模式）
     */
    fun isValid(context: Context): Boolean {
        val isNetworkConnected = isNetworkAvailable(context) || isHotspotEnabled(context)
        val ip = getLocalIpAddress(context)
        val isValid = isNetworkConnected && !TextUtils.isEmpty(ip) && ip != "0.0.0.0"

        if (!isNetworkConnected) {
            Toast.makeText(context, if (isHotspotEnabled(context)) "已开启热点，请让其他设备连接" else "网络未连接", Toast.LENGTH_SHORT).show()
        } else if (TextUtils.isEmpty(ip) || ip == "0.0.0.0") {
            Toast.makeText(context, "IP地址获取失败", Toast.LENGTH_SHORT).show()
        }
        return isValid
    }

    /**
     * 通用获取本机IP地址（优先热点网关IP → Wi-Fi → 移动流量）
     */
    fun getLocalIpAddress(context: Context): String {
        // 第一步：判断是否开启热点，优先返回热点网关IP
        if (isHotspotEnabled(context)) {
            // 先尝试读取热点配置的网关IP，兜底用默认值
            val hotspotIp = getHotspotGatewayIp() ?: HOTSPOT_DEFAULT_GATEWAY
            if (hotspotIp.isNotEmpty() && hotspotIp != "0.0.0.0") {
                return hotspotIp
            }
        }
        // 第二步：按原有逻辑获取Wi-Fi/移动流量IP
        return when (getCurrentNetworkType(context)) {
            NetworkType.WIFI -> getWifiIp(context)
            NetworkType.MOBILE -> getMobileIp()
            else -> ""
        }
    }

    // ==================== 新增：热点模式判断 & IP获取 ====================
    /**
     * 判断是否开启了手机热点（兼容Android 8.0+）
     */
    private fun isHotspotEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            // 反射调用热点状态判断（安卓未开放直接API）
            val method = wifiManager.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wifiManager) as Boolean
        } catch (e: Exception) {
            // 反射失败时，兜底判断是否有热点接口（wlan0/ap0）
            hasHotspotInterface()
        }
    }

    /**
     * 检测是否存在热点网络接口（兜底判断）
     */
    private fun hasHotspotInterface(): Boolean {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                // 热点接口名：ap0 / wlan0（不同厂商）
                if (ni.name == "ap0" || ni.name == "wlan0" && ni.isUp) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取热点网关IP（读取热点接口的IP）
     */
    private fun getHotspotGatewayIp(): String? {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                // 筛选热点接口
                if ((ni.name == "ap0" || ni.name == "wlan0") && ni.isUp && !ni.isLoopback) {
                    val addresses = ni.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: ""
                            if (ip.startsWith("192.168.")) { // 只保留局域网IP
                                return ip
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 原有逻辑（保留，仅补充注释） ====================
    private fun getCurrentNetworkType(context: Context): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                else -> NetworkType.NONE
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return NetworkType.NONE
            @Suppress("DEPRECATION")
            when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                ConnectivityManager.TYPE_MOBILE -> NetworkType.MOBILE
                else -> NetworkType.NONE
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return getCurrentNetworkType(context) != NetworkType.NONE
    }

    private fun getWifiIp(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) "" else {
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt == 0) "" else String.format(
                    "%d.%d.%d.%d",
                    (ipInt and 0xff),
                    (ipInt shr 8 and 0xff),
                    (ipInt shr 16 and 0xff),
                    (ipInt shr 24 and 0xff)
                ).takeIf { it != "0.0.0.0" } ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun getMobileIp(): String {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback || !ni.name.lowercase().startsWith("rmnet")) continue
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.isNotEmpty() && ip != "0.0.0.0") return ip
                    }
                }
            }
            getGeneralIp(excludeInterface = "wlan0")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun getGeneralIp(excludeInterface: String = ""): String {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback || ni.name == excludeInterface) continue
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.isNotEmpty() && ip != "0.0.0.0") return ip
                    }
                }
            }
            ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private enum class NetworkType {
        WIFI, MOBILE, NONE
    }
}