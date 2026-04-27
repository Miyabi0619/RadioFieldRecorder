package com.miyabi0619.radiofieldrecorder.core

import kotlin.math.ceil

data class ProbeSampleSnapshot(
    val timestamp: Long,
    val target: String,
    val success: Boolean,
    val latencyMs: Long?,
    val errorMessage: String?,
)

data class WifiSampleSnapshot(
    val timestamp: Long,
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
)

data class EventMarkerSnapshot(
    val timestamp: Long,
    val type: String,
    val label: String,
    val memo: String?,
)

data class SessionSummary(
    val probeCount: Int,
    val probeFailureRate: Double?,
    val averageLatencyMs: Double?,
    val maxLatencyMs: Long?,
    val p95LatencyMs: Long?,
    val averageWifiRssi: Double?,
    val minWifiRssi: Int?,
    val eventCount: Int,
)

object SessionSummaryCalculator {
    fun calculate(
        probes: List<ProbeSampleSnapshot>,
        wifiSamples: List<WifiSampleSnapshot>,
        events: List<EventMarkerSnapshot>,
    ): SessionSummary {
        val successfulLatencies = probes
            .filter { it.success }
            .mapNotNull { it.latencyMs }
            .sorted()
        val rssiValues = wifiSamples.mapNotNull { it.rssi }

        return SessionSummary(
            probeCount = probes.size,
            probeFailureRate = probes.takeIf { it.isNotEmpty() }?.let { samples ->
                samples.count { !it.success }.toDouble() / samples.size
            },
            averageLatencyMs = successfulLatencies.takeIf { it.isNotEmpty() }?.average(),
            maxLatencyMs = successfulLatencies.maxOrNull(),
            p95LatencyMs = percentileNearestRank(successfulLatencies, 95.0),
            averageWifiRssi = rssiValues.takeIf { it.isNotEmpty() }?.average(),
            minWifiRssi = rssiValues.minOrNull(),
            eventCount = events.size,
        )
    }

    private fun percentileNearestRank(
        sortedValues: List<Long>,
        percentile: Double,
    ): Long? {
        if (sortedValues.isEmpty()) return null
        val rank = ceil(percentile / 100.0 * sortedValues.size).toInt()
        return sortedValues[(rank - 1).coerceIn(sortedValues.indices)]
    }
}
