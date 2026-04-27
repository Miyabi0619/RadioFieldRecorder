package com.miyabi0619.radiofieldrecorder.recorder

import com.miyabi0619.radiofieldrecorder.data.local.ProbeTargetEntity
import com.miyabi0619.radiofieldrecorder.data.local.StoredProbeTargetType
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkProbeRunnerTest {
    @Test
    fun probe_tcpSuccessReturnsLatencyAndTargetMetadata() {
        ServerSocket(0).use { server ->
            val acceptThread = Thread {
                server.accept().use { accepted ->
                    accepted.getOutputStream().write(1)
                }
            }.also { it.start() }

            val runner = NetworkProbeRunner(FakeClock())
            val result = runner.probe(
                ProbeTargetEntity(
                    id = 7L,
                    sessionId = 1L,
                    label = "local tcp",
                    type = StoredProbeTargetType.TCP,
                    address = "127.0.0.1",
                    port = server.localPort,
                    path = null,
                    timeoutMs = 1_000L,
                ),
            )

            acceptThread.join(1_000L)
            assertTrue(result.success)
            assertEquals(7L, result.targetId)
            assertEquals("local tcp", result.targetLabel)
            assertEquals(1_000L, result.timestamp)
            assertEquals(5L, result.latencyMs)
            assertEquals(null, result.errorMessage)
        }
    }

    @Test
    fun probe_tcpMissingPortReturnsFailure() {
        val runner = NetworkProbeRunner(FakeClock())
        val result = runner.probe(
            ProbeTargetEntity(
                id = 0L,
                sessionId = 1L,
                label = "broken tcp",
                type = StoredProbeTargetType.TCP,
                address = "127.0.0.1",
                port = null,
                path = null,
                timeoutMs = 1_000L,
            ),
        )

        assertEquals(false, result.success)
        assertEquals(null, result.targetId)
        assertEquals(null, result.latencyMs)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun toEntity_keepsProbeResultFields() {
        val entity = NetworkProbeResult(
            targetId = 3L,
            targetLabel = "gateway",
            timestamp = 10L,
            success = true,
            latencyMs = 8L,
            errorMessage = null,
        ).toEntity(sessionId = 2L)

        assertEquals(2L, entity.sessionId)
        assertEquals(3L, entity.targetId)
        assertEquals("gateway", entity.targetLabel)
        assertEquals(10L, entity.timestamp)
        assertTrue(entity.success)
        assertEquals(8L, entity.latencyMs)
    }
}

private class FakeClock : RecorderClock {
    private var elapsedNanos = 0L

    override fun nowMillis(): Long = 1_000L

    override fun elapsedRealtimeNanos(): Long {
        elapsedNanos += 5_000_000L
        return elapsedNanos
    }
}
