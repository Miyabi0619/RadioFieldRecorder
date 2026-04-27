package com.miyabi0619.radiofieldrecorder.diagnostics

import com.miyabi0619.radiofieldrecorder.core.SessionSummary

object DiagnosticCommentGenerator {
    fun generate(summary: SessionSummary): List<String> {
        val comments = mutableListOf<String>()

        if (summary.probeCount == 0) {
            comments += "No probe samples are recorded yet."
        }

        val failureRate = summary.probeFailureRate
        if (failureRate != null && failureRate >= 0.1) {
            comments += "Probe failures are visible. Check Wi-Fi, AP, target host, firewall, or route before DDS/ROS2."
        }

        val p95Latency = summary.p95LatencyMs
        if (p95Latency != null && p95Latency >= 200L) {
            comments += "p95 latency is high. Look for congestion, roaming, AP load, or target host stalls."
        }

        val minRssi = summary.minWifiRssi
        if (minRssi != null && minRssi <= -75) {
            comments += "Wi-Fi RSSI is weak at least once. Radio distance, shielding, or band selection may matter."
        }

        if (summary.averageWifiRssi == null) {
            comments += "Wi-Fi RSSI is unavailable. Confirm nearby Wi-Fi/location permissions and device Wi-Fi state."
        }

        if (
            summary.probeCount > 0 &&
            (failureRate == null || failureRate == 0.0) &&
            (p95Latency == null || p95Latency < 100L) &&
            (summary.averageWifiRssi == null || summary.averageWifiRssi > -65.0)
        ) {
            comments += "Wi-Fi and IP probes look stable in this session. DDS/ROS2/QoS/app logic become stronger suspects."
        }

        return comments.distinct()
    }
}
