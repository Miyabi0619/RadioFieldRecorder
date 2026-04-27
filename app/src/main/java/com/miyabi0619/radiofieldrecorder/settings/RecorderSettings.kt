package com.miyabi0619.radiofieldrecorder.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RecorderSettings(
    val wifiSampleIntervalMs: Long = DefaultWifiSampleIntervalMs,
    val probeIntervalMs: Long = DefaultProbeIntervalMs,
    val probeTimeoutMs: Long = DefaultProbeTimeoutMs,
) {
    companion object {
        const val DefaultWifiSampleIntervalMs = 1_000L
        const val DefaultProbeIntervalMs = 2_000L
        const val DefaultProbeTimeoutMs = 1_000L
    }
}

private val Context.recorderSettingsDataStore by preferencesDataStore(
    name = "recorder_settings",
)

class RecorderSettingsStore(
    private val context: Context,
) {
    val settings: Flow<RecorderSettings> =
        context.recorderSettingsDataStore.data.map { preferences ->
            RecorderSettings(
                wifiSampleIntervalMs = preferences[Keys.WifiSampleIntervalMs]
                    ?: RecorderSettings.DefaultWifiSampleIntervalMs,
                probeIntervalMs = preferences[Keys.ProbeIntervalMs]
                    ?: RecorderSettings.DefaultProbeIntervalMs,
                probeTimeoutMs = preferences[Keys.ProbeTimeoutMs]
                    ?: RecorderSettings.DefaultProbeTimeoutMs,
            )
        }

    suspend fun update(settings: RecorderSettings) {
        require(settings.wifiSampleIntervalMs > 0) {
            "wifiSampleIntervalMs must be greater than 0."
        }
        require(settings.probeIntervalMs > 0) {
            "probeIntervalMs must be greater than 0."
        }
        require(settings.probeTimeoutMs > 0) {
            "probeTimeoutMs must be greater than 0."
        }

        context.recorderSettingsDataStore.edit { preferences ->
            preferences[Keys.WifiSampleIntervalMs] = settings.wifiSampleIntervalMs
            preferences[Keys.ProbeIntervalMs] = settings.probeIntervalMs
            preferences[Keys.ProbeTimeoutMs] = settings.probeTimeoutMs
        }
    }

    private object Keys {
        val WifiSampleIntervalMs: Preferences.Key<Long> = longPreferencesKey("wifi_sample_interval_ms")
        val ProbeIntervalMs: Preferences.Key<Long> = longPreferencesKey("probe_interval_ms")
        val ProbeTimeoutMs: Preferences.Key<Long> = longPreferencesKey("probe_timeout_ms")
    }
}
