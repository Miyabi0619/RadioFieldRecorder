package com.miyabi0619.radiofieldrecorder.export

import com.miyabi0619.radiofieldrecorder.data.local.EventMarkerEntity
import com.miyabi0619.radiofieldrecorder.data.local.ProbeSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.ProbeTargetEntity
import com.miyabi0619.radiofieldrecorder.data.local.SessionEntity
import com.miyabi0619.radiofieldrecorder.data.local.SessionStatus
import com.miyabi0619.radiofieldrecorder.data.local.StoredProbeTargetType
import com.miyabi0619.radiofieldrecorder.data.local.WifiSampleEntity
import com.miyabi0619.radiofieldrecorder.data.repository.SessionDetail
import com.miyabi0619.radiofieldrecorder.core.SessionSummary
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExportArchiveBuilderTest {
    @Test
    fun build_createsZipWithExpectedCsvFiles() {
        val archive = SessionExportArchiveBuilder.build(
            SessionDetail(
                session = SessionEntity(
                    id = 5L,
                    name = "Field Test",
                    memo = null,
                    startedAt = 100L,
                    endedAt = 200L,
                    status = SessionStatus.STOPPED,
                    wifiSampleIntervalMs = 1_000L,
                    probeIntervalMs = 2_000L,
                    probeTimeoutMs = 1_000L,
                    rosDomainId = 0,
                ),
                targets = listOf(
                    ProbeTargetEntity(
                        id = 1L,
                        sessionId = 5L,
                        label = "ROS2 PC",
                        type = StoredProbeTargetType.TCP,
                        address = "192.168.1.30",
                        port = 7411,
                        path = null,
                        timeoutMs = 1_000L,
                    ),
                ),
                wifiSamples = listOf(
                    WifiSampleEntity(1L, 5L, 110L, "lab", "aa:bb", -55, 866, 5200, "WIFI"),
                ),
                probeSamples = listOf(
                    ProbeSampleEntity(1L, 5L, 1L, 120L, "ROS2 PC", true, 10L, null),
                ),
                events = listOf(
                    EventMarkerEntity(1L, 5L, 130L, "Delay", "Delay", "lag"),
                ),
                summary = SessionSummary(
                    probeCount = 1,
                    probeFailureRate = 0.0,
                    averageLatencyMs = 10.0,
                    maxLatencyMs = 10L,
                    p95LatencyMs = 10L,
                    averageWifiRssi = -55.0,
                    minWifiRssi = -55,
                    eventCount = 1,
                ),
            ),
        )

        assertEquals("radio_field_recorder_5_field_test.zip", archive.fileName)
        val entries = unzipTextEntries(archive.bytes)
        assertEquals(
            setOf("summary.csv", "wifi_samples.csv", "probe_samples.csv", "events.csv", "conditions.csv"),
            entries.keys,
        )
        assertTrue(entries.getValue("summary.csv").contains("probeCount,1"))
        assertTrue(entries.getValue("conditions.csv").contains("rosDomainId,0"))
        assertTrue(entries.getValue("events.csv").contains("130,Delay,Delay,lag"))
    }

    private fun unzipTextEntries(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }
}
