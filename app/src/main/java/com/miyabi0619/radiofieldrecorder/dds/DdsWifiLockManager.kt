package com.miyabi0619.radiofieldrecorder.dds

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

class DdsWifiLockManager(context: Context) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Synchronized
    fun acquire() {
        val wifiLock = wifiLock ?: wifiManager.createWifiLock(wifiLockMode(), WifiLockTag).also {
            it.setReferenceCounted(false)
            this.wifiLock = it
        }
        val multicastLock = multicastLock ?: wifiManager.createMulticastLock(MulticastLockTag).also {
            it.setReferenceCounted(false)
            this.multicastLock = it
        }

        runCatching {
            if (!wifiLock.isHeld) {
                wifiLock.acquire()
            }
        }.onFailure { error ->
            Log.w(Tag, "Failed to acquire Wi-Fi lock for DDS discovery", error)
        }

        runCatching {
            if (!multicastLock.isHeld) {
                multicastLock.acquire()
            }
        }.onFailure { error ->
            Log.w(Tag, "Failed to acquire multicast lock for DDS discovery", error)
        }
    }

    @Synchronized
    fun release() {
        multicastLock?.let { lock ->
            runCatching {
                if (lock.isHeld) {
                    lock.release()
                }
            }.onFailure { error ->
                Log.w(Tag, "Failed to release multicast lock for DDS discovery", error)
            }
        }
        wifiLock?.let { lock ->
            runCatching {
                if (lock.isHeld) {
                    lock.release()
                }
            }.onFailure { error ->
                Log.w(Tag, "Failed to release Wi-Fi lock for DDS discovery", error)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiLockMode(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }

    private companion object {
        private const val Tag = "DdsWifiLockManager"
        private const val WifiLockTag = "RadioFieldRecorder:DdsWifi"
        private const val MulticastLockTag = "RadioFieldRecorder:DdsMulticast"
    }
}
