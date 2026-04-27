package com.miyabi0619.radiofieldrecorder.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryCalculatorTest {
    @Test
    fun calculate_usesSuccessfulProbeLatenciesAndAllProbeFailures() {
        val probes = listOf(
            ProbeSampleSnapshot(1L, "pc", success = true, latencyMs = 10L, errorMessage = null),
            ProbeSampleSnapshot(2L, "pc", success = true, latencyMs = 20L, errorMessage = null),
            ProbeSampleSnapshot(3L, "pc", success = false, latencyMs = null, errorMessage = "timeout"),
            ProbeSampleSnapshot(4L, "pc", success = true, latencyMs = 40L, errorMessage = null),
        )
        val wifi = listOf(
            WifiSampleSnapshot(1L, "lab", "aa:bb", -50, 866, 5200),
            WifiSampleSnapshot(2L, "lab", "aa:bb", -70, 433, 5200),
        )
        val events = listOf(
            EventMarkerSnapshot(3L, "Delay", "Delay", "operation lag"),
        )

        val summary = SessionSummaryCalculator.calculate(probes, wifi, events)

        assertEquals(4, summary.probeCount)
        assertEquals(0.25, summary.probeFailureRate!!, 0.0001)
        assertEquals(70.0 / 3.0, summary.averageLatencyMs!!, 0.0001)
        assertEquals(40L, summary.maxLatencyMs)
        assertEquals(40L, summary.p95LatencyMs)
        assertEquals(-60.0, summary.averageWifiRssi!!, 0.0001)
        assertEquals(-70, summary.minWifiRssi)
        assertEquals(1, summary.eventCount)
    }

    @Test
    fun calculate_returnsNullMetricsForEmptySamples() {
        val summary = SessionSummaryCalculator.calculate(
            probes = emptyList(),
            wifiSamples = emptyList(),
            events = emptyList(),
        )

        assertEquals(0, summary.probeCount)
        assertEquals(null, summary.probeFailureRate)
        assertEquals(null, summary.averageLatencyMs)
        assertEquals(null, summary.maxLatencyMs)
        assertEquals(null, summary.p95LatencyMs)
        assertEquals(null, summary.averageWifiRssi)
        assertEquals(null, summary.minWifiRssi)
        assertEquals(0, summary.eventCount)
    }
}
