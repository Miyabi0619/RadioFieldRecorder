package com.miyabi0619.radiofieldrecorder.data.repository

import com.miyabi0619.radiofieldrecorder.core.EventMarkerSnapshot
import com.miyabi0619.radiofieldrecorder.core.ProbeSampleSnapshot
import com.miyabi0619.radiofieldrecorder.core.ProbeTarget
import com.miyabi0619.radiofieldrecorder.core.ProbeTargetType
import com.miyabi0619.radiofieldrecorder.core.SessionSummary
import com.miyabi0619.radiofieldrecorder.core.SessionSummaryCalculator
import com.miyabi0619.radiofieldrecorder.core.WifiSampleSnapshot
import com.miyabi0619.radiofieldrecorder.data.local.EventMarkerDao
import com.miyabi0619.radiofieldrecorder.data.local.EventMarkerEntity
import com.miyabi0619.radiofieldrecorder.data.local.DdsEndpointSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.DdsEndpointSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.DdsParticipantSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.DdsParticipantSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.ProbeSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.ProbeSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.ProbeTargetDao
import com.miyabi0619.radiofieldrecorder.data.local.ProbeTargetEntity
import com.miyabi0619.radiofieldrecorder.data.local.RadioFieldRecorderDatabase
import com.miyabi0619.radiofieldrecorder.data.local.SessionDao
import com.miyabi0619.radiofieldrecorder.data.local.SessionEntity
import com.miyabi0619.radiofieldrecorder.data.local.SessionStatus
import com.miyabi0619.radiofieldrecorder.data.local.StoredProbeTargetType
import com.miyabi0619.radiofieldrecorder.data.local.WifiSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.WifiSampleEntity
import com.miyabi0619.radiofieldrecorder.settings.RecorderSettings
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class CreateSessionRequest(
    val name: String,
    val memo: String?,
    val targets: List<ProbeTarget>,
    val settings: RecorderSettings,
    val rosDomainId: Int?,
    val startedAt: Long,
)

data class SessionDetail(
    val session: SessionEntity,
    val targets: List<ProbeTargetEntity>,
    val wifiSamples: List<WifiSampleEntity>,
    val probeSamples: List<ProbeSampleEntity>,
    val events: List<EventMarkerEntity>,
    val ddsParticipantSamples: List<DdsParticipantSampleEntity>,
    val ddsEndpointSamples: List<DdsEndpointSampleEntity>,
    val summary: SessionSummary,
)

class RecordingRepository(
    private val sessionDao: SessionDao,
    private val probeTargetDao: ProbeTargetDao,
    private val wifiSampleDao: WifiSampleDao,
    private val probeSampleDao: ProbeSampleDao,
    private val eventMarkerDao: EventMarkerDao,
    private val ddsParticipantSampleDao: DdsParticipantSampleDao,
    private val ddsEndpointSampleDao: DdsEndpointSampleDao,
    private val runLongTransaction: suspend (suspend () -> Long) -> Long,
) {
    constructor(database: RadioFieldRecorderDatabase) : this(
        sessionDao = database.sessionDao(),
        probeTargetDao = database.probeTargetDao(),
        wifiSampleDao = database.wifiSampleDao(),
        probeSampleDao = database.probeSampleDao(),
        eventMarkerDao = database.eventMarkerDao(),
        ddsParticipantSampleDao = database.ddsParticipantSampleDao(),
        ddsEndpointSampleDao = database.ddsEndpointSampleDao(),
        runLongTransaction = { block -> database.withTransaction { block() } },
    )

    fun observeSessions(): Flow<List<SessionEntity>> =
        sessionDao.observeSessions()

    suspend fun getLatestSession(): SessionEntity? =
        sessionDao.observeSessions().first().firstOrNull()

    suspend fun getLatestRunningSession(): SessionEntity? =
        sessionDao.getLatestSessionByStatus(SessionStatus.RUNNING)

    suspend fun createSession(request: CreateSessionRequest): Long {
        require(request.name.isNotBlank()) { "Session name is required." }
        require(request.settings.wifiSampleIntervalMs > 0) {
            "wifiSampleIntervalMs must be greater than 0."
        }
        require(request.settings.probeIntervalMs > 0) {
            "probeIntervalMs must be greater than 0."
        }
        require(request.settings.probeTimeoutMs > 0) {
            "probeTimeoutMs must be greater than 0."
        }

        val session = SessionEntity(
            name = request.name.trim(),
            memo = request.memo?.trim()?.ifBlank { null },
            startedAt = request.startedAt,
            endedAt = null,
            status = SessionStatus.RUNNING,
            wifiSampleIntervalMs = request.settings.wifiSampleIntervalMs,
            probeIntervalMs = request.settings.probeIntervalMs,
            probeTimeoutMs = request.settings.probeTimeoutMs,
            rosDomainId = request.rosDomainId,
        )

        return runLongTransaction {
            val sessionId = sessionDao.insert(session)
            val targetEntities = request.targets.map { target ->
                target.toEntity(sessionId)
            }
            if (targetEntities.isNotEmpty()) {
                probeTargetDao.insertAll(targetEntities)
            }
            sessionId
        }
    }

    suspend fun stopSession(
        sessionId: Long,
        endedAt: Long,
    ) {
        sessionDao.stopSession(sessionId = sessionId, endedAt = endedAt)
    }

    suspend fun addWifiSample(sample: WifiSampleEntity): Long =
        wifiSampleDao.insert(sample)

    suspend fun addProbeSample(sample: ProbeSampleEntity): Long =
        probeSampleDao.insert(sample)

    suspend fun addEvent(event: EventMarkerEntity): Long =
        eventMarkerDao.insert(event)

    suspend fun addDdsParticipantSamples(samples: List<DdsParticipantSampleEntity>): List<Long> =
        if (samples.isEmpty()) emptyList() else ddsParticipantSampleDao.insertAll(samples)

    suspend fun addDdsEndpointSamples(samples: List<DdsEndpointSampleEntity>): List<Long> =
        if (samples.isEmpty()) emptyList() else ddsEndpointSampleDao.insertAll(samples)

    suspend fun addEvent(
        sessionId: Long,
        timestamp: Long,
        type: String,
        label: String,
        memo: String?,
    ): Long =
        addEvent(
            EventMarkerEntity(
                sessionId = sessionId,
                timestamp = timestamp,
                type = type,
                label = label,
                memo = memo?.trim()?.ifBlank { null },
            ),
        )

    suspend fun getSessionDetail(sessionId: Long): SessionDetail? {
        val session = sessionDao.getSession(sessionId) ?: return null
        val targets = probeTargetDao.getTargetsForSession(sessionId)
        val wifiSamples = wifiSampleDao.getSamplesForSession(sessionId)
        val probeSamples = probeSampleDao.getSamplesForSession(sessionId)
        val events = eventMarkerDao.getEventsForSession(sessionId)
        val ddsParticipantSamples = ddsParticipantSampleDao.getSamplesForSession(sessionId)
        val ddsEndpointSamples = ddsEndpointSampleDao.getSamplesForSession(sessionId)
        val summary = SessionSummaryCalculator.calculate(
            probes = probeSamples.map { it.toSnapshot() },
            wifiSamples = wifiSamples.map { it.toSnapshot() },
            events = events.map { it.toSnapshot() },
        )

        return SessionDetail(
            session = session,
            targets = targets,
            wifiSamples = wifiSamples,
            probeSamples = probeSamples,
            events = events,
            ddsParticipantSamples = ddsParticipantSamples,
            ddsEndpointSamples = ddsEndpointSamples,
            summary = summary,
        )
    }
}

private fun ProbeTarget.toEntity(sessionId: Long): ProbeTargetEntity =
    ProbeTargetEntity(
        sessionId = sessionId,
        label = label,
        type = when (type) {
            ProbeTargetType.HTTP -> StoredProbeTargetType.HTTP
            ProbeTargetType.TCP -> StoredProbeTargetType.TCP
        },
        address = address,
        port = port,
        path = path,
        timeoutMs = timeoutMs.toLong(),
    )

private fun ProbeSampleEntity.toSnapshot(): ProbeSampleSnapshot =
    ProbeSampleSnapshot(
        timestamp = timestamp,
        target = targetLabel,
        success = success,
        latencyMs = latencyMs,
        errorMessage = errorMessage,
    )

private fun WifiSampleEntity.toSnapshot(): WifiSampleSnapshot =
    WifiSampleSnapshot(
        timestamp = timestamp,
        ssid = ssid,
        bssid = bssid,
        rssi = rssi,
        linkSpeedMbps = linkSpeedMbps,
        frequencyMhz = frequencyMhz,
    )

private fun EventMarkerEntity.toSnapshot(): EventMarkerSnapshot =
    EventMarkerSnapshot(
        timestamp = timestamp,
        type = type,
        label = label,
        memo = memo,
    )
