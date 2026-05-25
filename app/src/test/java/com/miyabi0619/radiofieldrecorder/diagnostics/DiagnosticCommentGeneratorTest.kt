package com.miyabi0619.radiofieldrecorder.diagnostics

import com.miyabi0619.radiofieldrecorder.core.SessionSummary
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCommentGeneratorTest {
    @Test
    fun generate_pointsToNetworkWhenProbeFailuresAreHigh() {
        val comments = DiagnosticCommentGenerator.generate(
            SessionSummary(
                probeCount = 10,
                probeFailureRate = 0.2,
                averageLatencyMs = 20.0,
                maxLatencyMs = 40L,
                p95LatencyMs = 40L,
                averageWifiRssi = -55.0,
                minWifiRssi = -60,
                eventCount = 0,
            ),
        )

        assertTrue(comments.any { it.contains("Wi-Fi、AP、接続先ホスト") })
    }

    @Test
    fun generate_pointsToAppSideWhenRadioAndIpLookStable() {
        val comments = DiagnosticCommentGenerator.generate(
            SessionSummary(
                probeCount = 10,
                probeFailureRate = 0.0,
                averageLatencyMs = 20.0,
                maxLatencyMs = 30L,
                p95LatencyMs = 30L,
                averageWifiRssi = -50.0,
                minWifiRssi = -55,
                eventCount = 0,
            ),
        )

        assertTrue(comments.any { it.contains("DDS/ROS2/QoS/アプリ側") })
    }

    @Test
    fun generate_mentionsMissingWifiPermissionWhenRssiUnavailable() {
        val comments = DiagnosticCommentGenerator.generate(
            SessionSummary(
                probeCount = 0,
                probeFailureRate = null,
                averageLatencyMs = null,
                maxLatencyMs = null,
                p95LatencyMs = null,
                averageWifiRssi = null,
                minWifiRssi = null,
                eventCount = 0,
            ),
        )

        assertTrue(comments.any { it.contains("権限") })
    }
}
