package com.miyabi0619.radiofieldrecorder.recorder

import com.miyabi0619.radiofieldrecorder.data.local.ProbeTargetEntity
import com.miyabi0619.radiofieldrecorder.data.local.ProbeSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.StoredProbeTargetType
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.roundToLong

data class NetworkProbeResult(
    val targetId: Long?,
    val targetLabel: String,
    val timestamp: Long,
    val success: Boolean,
    val latencyMs: Long?,
    val errorMessage: String?,
)

class NetworkProbeRunner(
    private val clock: RecorderClock = SystemRecorderClock,
) {
    fun probe(target: ProbeTargetEntity): NetworkProbeResult {
        val startedNanos = clock.elapsedRealtimeNanos()
        val timestamp = clock.nowMillis()

        return runCatching {
            when (target.type) {
                StoredProbeTargetType.HTTP -> probeHttp(target)
                StoredProbeTargetType.TCP -> probeTcp(target)
            }
        }.fold(
            onSuccess = {
                NetworkProbeResult(
                    targetId = target.id.takeIf { it != 0L },
                    targetLabel = target.label,
                    timestamp = timestamp,
                    success = true,
                    latencyMs = elapsedMsSince(startedNanos),
                    errorMessage = null,
                )
            },
            onFailure = { error ->
                NetworkProbeResult(
                    targetId = target.id.takeIf { it != 0L },
                    targetLabel = target.label,
                    timestamp = timestamp,
                    success = false,
                    latencyMs = null,
                    errorMessage = error.message ?: error::class.java.simpleName,
                )
            },
        )
    }

    private fun probeHttp(target: ProbeTargetEntity) {
        val connection = URL(target.address).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = target.timeoutMs.toInt()
        connection.readTimeout = target.timeoutMs.toInt()
        connection.instanceFollowRedirects = false

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..399) {
                error("HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun probeTcp(target: ProbeTargetEntity) {
        val port = requireNotNull(target.port) { "TCP target port is required." }
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(target.address, port),
                target.timeoutMs.toInt(),
            )
        }
    }

    private fun elapsedMsSince(startedNanos: Long): Long =
        ((clock.elapsedRealtimeNanos() - startedNanos).toDouble() / 1_000_000.0)
            .roundToLong()
            .coerceAtLeast(0L)
}

fun NetworkProbeResult.toEntity(sessionId: Long): ProbeSampleEntity =
    ProbeSampleEntity(
        sessionId = sessionId,
        targetId = targetId,
        timestamp = timestamp,
        targetLabel = targetLabel,
        success = success,
        latencyMs = latencyMs,
        errorMessage = errorMessage,
    )
