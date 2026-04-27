package com.miyabi0619.radiofieldrecorder.recorder

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import com.miyabi0619.radiofieldrecorder.data.local.WifiSampleEntity

data class WifiSnapshot(
    val timestamp: Long,
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val networkType: String?,
)

class WifiMonitor(
    private val context: Context,
    private val clock: RecorderClock = SystemRecorderClock,
) {
    fun sample(): WifiSnapshot {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val wifiInfo = capabilities?.transportInfo as? WifiInfo

        return WifiSnapshot(
            timestamp = clock.nowMillis(),
            ssid = wifiInfo?.ssid?.cleanWifiValue(),
            bssid = wifiInfo?.bssid?.cleanWifiValue(),
            rssi = wifiInfo?.rssi,
            linkSpeedMbps = wifiInfo?.linkSpeed,
            frequencyMhz = wifiInfo?.frequency,
            networkType = networkType(capabilities),
        )
    }

    private fun networkType(capabilities: NetworkCapabilities?): String? =
        when {
            capabilities == null -> null
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            else -> "OTHER"
        }
}

fun WifiSnapshot.toEntity(sessionId: Long): WifiSampleEntity =
    WifiSampleEntity(
        sessionId = sessionId,
        timestamp = timestamp,
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        linkSpeedMbps = linkSpeedMbps,
        frequencyMhz = frequencyMhz,
        networkType = networkType,
    )

internal fun String.cleanWifiValue(): String? {
    val trimmed = trim().trim('"')
    return trimmed
        .takeUnless { it.isBlank() }
        ?.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }
        ?.takeUnless { it == "02:00:00:00:00:00" }
}
