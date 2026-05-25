package com.miyabi0619.radiofieldrecorder.data.repository

import com.miyabi0619.radiofieldrecorder.core.ProbeTargetParser
import com.miyabi0619.radiofieldrecorder.core.ProbeTargetParseResult
import com.miyabi0619.radiofieldrecorder.data.local.DdsEndpointSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.DdsEndpointSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.DdsParticipantSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.DdsParticipantSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.EventMarkerDao
import com.miyabi0619.radiofieldrecorder.data.local.EventMarkerEntity
import com.miyabi0619.radiofieldrecorder.data.local.ProbeSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.ProbeSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.ProbeTargetDao
import com.miyabi0619.radiofieldrecorder.data.local.ProbeTargetEntity
import com.miyabi0619.radiofieldrecorder.data.local.SessionDao
import com.miyabi0619.radiofieldrecorder.data.local.SessionEntity
import com.miyabi0619.radiofieldrecorder.data.local.SessionStatus
import com.miyabi0619.radiofieldrecorder.data.local.WifiSampleDao
import com.miyabi0619.radiofieldrecorder.data.local.WifiSampleEntity
import com.miyabi0619.radiofieldrecorder.settings.RecorderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingRepositoryTest {
    @Test
    fun createSession_storesSessionSettingsAndTargets() = runBlocking {
        val fakes = FakeRecordingStore()
        val repository = fakes.repository()
        val target = ProbeTargetParser.parseTcp(
            label = "ROS2 PC DDS user unicast",
            host = "192.168.1.30",
            port = 7411,
        ) as ProbeTargetParseResult.Success

        val sessionId = repository.createSession(
            CreateSessionRequest(
                name = " Field test ",
                memo = "  near AP ",
                targets = listOf(target.target),
                settings = RecorderSettings(
                    wifiSampleIntervalMs = 1_500L,
                    probeIntervalMs = 3_000L,
                    probeTimeoutMs = 800L,
                ),
                rosDomainId = 0,
                startedAt = 100L,
            ),
        )

        val session = fakes.sessionDao.getSession(sessionId)
        val targets = fakes.probeTargetDao.getTargetsForSession(sessionId)

        assertNotNull(session)
        assertEquals("Field test", session!!.name)
        assertEquals("near AP", session.memo)
        assertEquals(SessionStatus.RUNNING, session.status)
        assertEquals(1_500L, session.wifiSampleIntervalMs)
        assertEquals(3_000L, session.probeIntervalMs)
        assertEquals(800L, session.probeTimeoutMs)
        assertEquals(0, session.rosDomainId)
        assertEquals(1, targets.size)
        assertEquals(sessionId, targets.single().sessionId)
        assertEquals("ROS2 PC DDS user unicast", targets.single().label)
        assertTrue(fakes.transactionRan)
    }

    @Test
    fun getSessionDetail_returnsSamplesAndCalculatedSummary() = runBlocking {
        val fakes = FakeRecordingStore()
        val repository = fakes.repository()
        val sessionId = repository.createSession(
            CreateSessionRequest(
                name = "Test",
                memo = null,
                targets = emptyList(),
                settings = RecorderSettings(),
                rosDomainId = null,
                startedAt = 100L,
            ),
        )

        repository.addWifiSample(
            WifiSampleEntity(
                sessionId = sessionId,
                timestamp = 101L,
                ssid = "lab",
                bssid = "aa:bb",
                rssi = -55,
                linkSpeedMbps = 866,
                frequencyMhz = 5200,
                networkType = "WIFI",
            ),
        )
        repository.addProbeSample(
            ProbeSampleEntity(
                sessionId = sessionId,
                targetId = null,
                timestamp = 102L,
                targetLabel = "gateway",
                success = true,
                latencyMs = 12L,
                errorMessage = null,
            ),
        )
        repository.addProbeSample(
            ProbeSampleEntity(
                sessionId = sessionId,
                targetId = null,
                timestamp = 103L,
                targetLabel = "gateway",
                success = false,
                latencyMs = null,
                errorMessage = "timeout",
            ),
        )
        repository.addEvent(
            EventMarkerEntity(
                sessionId = sessionId,
                timestamp = 104L,
                type = "Delay",
                label = "Delay",
                memo = "operator noticed lag",
            ),
        )
        repository.addDdsParticipantSamples(
            listOf(
                DdsParticipantSampleEntity(
                    sessionId = sessionId,
                    timestamp = 105L,
                    participantGuid = "participant-1",
                    participantName = "ros-pc",
                    status = "VISIBLE",
                    firstSeenAt = 105L,
                    lastSeenAt = 105L,
                ),
            ),
        )
        repository.addDdsEndpointSamples(
            listOf(
                DdsEndpointSampleEntity(
                    sessionId = sessionId,
                    timestamp = 106L,
                    endpointGuid = "endpoint-1",
                    participantGuid = "participant-1",
                    topicName = "rt/chatter",
                    typeName = "std_msgs::msg::dds_::String_",
                    kind = "WRITER",
                    status = "VISIBLE",
                    firstSeenAt = 106L,
                    lastSeenAt = 106L,
                ),
            ),
        )

        val detail = repository.getSessionDetail(sessionId)

        assertNotNull(detail)
        assertEquals(1, detail!!.wifiSamples.size)
        assertEquals(2, detail.probeSamples.size)
        assertEquals(1, detail.events.size)
        assertEquals(1, detail.ddsParticipantSamples.size)
        assertEquals(1, detail.ddsEndpointSamples.size)
        assertEquals(2, detail.summary.probeCount)
        assertEquals(0.5, detail.summary.probeFailureRate!!, 0.0001)
        assertEquals(12.0, detail.summary.averageLatencyMs!!, 0.0001)
        assertEquals(-55.0, detail.summary.averageWifiRssi!!, 0.0001)
    }
}

private class FakeRecordingStore {
    val sessionDao = FakeSessionDao()
    val probeTargetDao = FakeProbeTargetDao()
    val wifiSampleDao = FakeWifiSampleDao()
    val probeSampleDao = FakeProbeSampleDao()
    val eventMarkerDao = FakeEventMarkerDao()
    val ddsParticipantSampleDao = FakeDdsParticipantSampleDao()
    val ddsEndpointSampleDao = FakeDdsEndpointSampleDao()
    var transactionRan = false

    fun repository(): RecordingRepository =
        RecordingRepository(
            sessionDao = sessionDao,
            probeTargetDao = probeTargetDao,
            wifiSampleDao = wifiSampleDao,
            probeSampleDao = probeSampleDao,
            eventMarkerDao = eventMarkerDao,
            ddsParticipantSampleDao = ddsParticipantSampleDao,
            ddsEndpointSampleDao = ddsEndpointSampleDao,
            runLongTransaction = { block ->
                transactionRan = true
                block()
            },
        )
}

private class FakeSessionDao : SessionDao {
    private val sessions = mutableListOf<SessionEntity>()
    private var nextId = 1L

    override suspend fun insert(session: SessionEntity): Long {
        val id = nextId++
        sessions += session.copy(id = id)
        return id
    }

    override suspend fun stopSession(
        sessionId: Long,
        endedAt: Long,
        status: SessionStatus,
    ) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index >= 0) {
            sessions[index] = sessions[index].copy(endedAt = endedAt, status = status)
        }
    }

    override fun observeSessions(): Flow<List<SessionEntity>> =
        flowOf(sessions.sortedByDescending { it.startedAt })

    override suspend fun getSession(sessionId: Long): SessionEntity? =
        sessions.firstOrNull { it.id == sessionId }

    override suspend fun getLatestSessionByStatus(status: SessionStatus): SessionEntity? =
        sessions
            .filter { it.status == status }
            .maxByOrNull { it.startedAt }
}

private class FakeProbeTargetDao : ProbeTargetDao {
    private val targets = mutableListOf<ProbeTargetEntity>()
    private var nextId = 1L

    override suspend fun insert(target: ProbeTargetEntity): Long {
        val id = nextId++
        targets += target.copy(id = id)
        return id
    }

    override suspend fun insertAll(targets: List<ProbeTargetEntity>): List<Long> =
        targets.map { insert(it) }

    override suspend fun getTargetsForSession(sessionId: Long): List<ProbeTargetEntity> =
        targets.filter { it.sessionId == sessionId }.sortedBy { it.id }
}

private class FakeWifiSampleDao : WifiSampleDao {
    private val samples = mutableListOf<WifiSampleEntity>()
    private var nextId = 1L

    override suspend fun insert(sample: WifiSampleEntity): Long {
        val id = nextId++
        samples += sample.copy(id = id)
        return id
    }

    override suspend fun getSamplesForSession(sessionId: Long): List<WifiSampleEntity> =
        samples.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
}

private class FakeProbeSampleDao : ProbeSampleDao {
    private val samples = mutableListOf<ProbeSampleEntity>()
    private var nextId = 1L

    override suspend fun insert(sample: ProbeSampleEntity): Long {
        val id = nextId++
        samples += sample.copy(id = id)
        return id
    }

    override suspend fun getSamplesForSession(sessionId: Long): List<ProbeSampleEntity> =
        samples.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
}

private class FakeEventMarkerDao : EventMarkerDao {
    private val events = mutableListOf<EventMarkerEntity>()
    private var nextId = 1L

    override suspend fun insert(event: EventMarkerEntity): Long {
        val id = nextId++
        events += event.copy(id = id)
        return id
    }

    override suspend fun getEventsForSession(sessionId: Long): List<EventMarkerEntity> =
        events.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
}

private class FakeDdsParticipantSampleDao : DdsParticipantSampleDao {
    private val samples = mutableListOf<DdsParticipantSampleEntity>()
    private var nextId = 1L

    override suspend fun insertAll(samples: List<DdsParticipantSampleEntity>): List<Long> =
        samples.map { sample ->
            val id = nextId++
            this.samples += sample.copy(id = id)
            id
        }

    override suspend fun getSamplesForSession(sessionId: Long): List<DdsParticipantSampleEntity> =
        samples.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
}

private class FakeDdsEndpointSampleDao : DdsEndpointSampleDao {
    private val samples = mutableListOf<DdsEndpointSampleEntity>()
    private var nextId = 1L

    override suspend fun insertAll(samples: List<DdsEndpointSampleEntity>): List<Long> =
        samples.map { sample ->
            val id = nextId++
            this.samples += sample.copy(id = id)
            id
        }

    override suspend fun getSamplesForSession(sessionId: Long): List<DdsEndpointSampleEntity> =
        samples.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
}
