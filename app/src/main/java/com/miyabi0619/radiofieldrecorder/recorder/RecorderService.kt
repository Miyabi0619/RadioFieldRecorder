package com.miyabi0619.radiofieldrecorder.recorder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.miyabi0619.radiofieldrecorder.data.local.DdsEndpointSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.DdsParticipantSampleEntity
import com.miyabi0619.radiofieldrecorder.data.local.RadioFieldRecorderDatabase
import com.miyabi0619.radiofieldrecorder.data.repository.RecordingRepository
import com.miyabi0619.radiofieldrecorder.dds.DdsDiscoveryNativeBridge
import com.miyabi0619.radiofieldrecorder.dds.DdsDiscoverySnapshot
import com.miyabi0619.radiofieldrecorder.dds.DdsWifiLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecorderService : Service() {
    private lateinit var repository: RecordingRepository
    private lateinit var wifiMonitor: WifiMonitor
    private lateinit var networkProbeRunner: NetworkProbeRunner
    private lateinit var ddsWifiLockManager: DdsWifiLockManager
    private var ddsDiscoveryNativeBridge: DdsDiscoveryNativeBridge? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordingJob: Job? = null
    private var activeSessionId: Long? = null

    override fun onCreate() {
        super.onCreate()
        val database = RadioFieldRecorderDatabase.create(this)
        repository = RecordingRepository(database)
        wifiMonitor = WifiMonitor(this)
        networkProbeRunner = NetworkProbeRunner()
        ddsWifiLockManager = DdsWifiLockManager(this)
        RecorderNotification.ensureChannel(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        return when (intent?.action) {
            ActionStart -> {
                handleStart(intent)
                START_NOT_STICKY
            }
            ActionStop -> {
                handleStop()
                START_NOT_STICKY
            }
            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        recordingJob?.cancel()
        stopDdsDiscovery()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        val sessionId = intent.getLongExtra(ExtraSessionId, MissingSessionId)
        val sessionName = intent.getStringExtra(ExtraSessionName) ?: "記録"
        if (sessionId == MissingSessionId) {
            stopSelf()
            return
        }

        activeSessionId = sessionId
        startRecorderForeground(sessionName)
        recordingJob?.cancel()
        recordingJob = serviceScope.launch {
            runRecording(sessionId)
        }
    }

    private fun handleStop() {
        val sessionId = activeSessionId
        recordingJob?.cancel()
        recordingJob = null
        activeSessionId = null
        stopDdsDiscovery()

        if (sessionId != null) {
            serviceScope.launch {
                runCatching {
                    repository.stopSession(sessionId, SystemRecorderClock.nowMillis())
                }.onFailure { error ->
                    Log.w(Tag, "Failed to stop session $sessionId", error)
                }
                stopForegroundCompat()
                stopSelf()
            }
        } else {
            stopForegroundCompat()
            stopSelf()
        }
    }

    private suspend fun runRecording(sessionId: Long) {
        val detail = repository.getSessionDetail(sessionId)
        if (detail == null) {
            stopSelf()
            return
        }

        val wifiIntervalMs = detail.session.wifiSampleIntervalMs
        val probeIntervalMs = detail.session.probeIntervalMs
        val targets = detail.targets
        val ddsJob = detail.session.rosDomainId?.let { domainId ->
            val bridge = startDdsDiscovery(domainId)
            bridge?.let {
                serviceScope.launchLoop(intervalMs = probeIntervalMs) {
                    val snapshot = it.snapshot()
                    val sampledAt = SystemRecorderClock.nowMillis()
                    repository.addDdsParticipantSamples(
                        snapshot.toParticipantEntities(
                            sessionId = sessionId,
                            sampledAt = sampledAt,
                        ),
                    )
                    repository.addDdsEndpointSamples(
                        snapshot.toEndpointEntities(
                            sessionId = sessionId,
                            sampledAt = sampledAt,
                        ),
                    )
                }
            }
        }

        val wifiJob = serviceScope.launchLoop(intervalMs = wifiIntervalMs) {
            val sample = wifiMonitor.sample()
            repository.addWifiSample(sample.toEntity(sessionId))
        }
        val probeJob = serviceScope.launchLoop(intervalMs = probeIntervalMs) {
            targets.forEach { target ->
                val result = withContext(Dispatchers.IO) {
                    networkProbeRunner.probe(target)
                }
                repository.addProbeSample(result.toEntity(sessionId))
            }
        }

        wifiJob.join()
        probeJob.join()
        ddsJob?.join()
    }

    private fun startDdsDiscovery(domainId: Int): DdsDiscoveryNativeBridge? {
        val bridge = ddsDiscoveryNativeBridge ?: runCatching {
            DdsDiscoveryNativeBridge()
        }.onFailure { error ->
            Log.w(Tag, "Failed to load DDS discovery monitor", error)
        }.getOrNull()

        ddsDiscoveryNativeBridge = bridge
        if (bridge == null) {
            return null
        }

        ddsWifiLockManager.acquire()
        return bridge.takeIf {
            runCatching { it.start(domainId) }
                .onFailure { error -> Log.w(Tag, "Failed to start DDS discovery monitor", error) }
                .getOrDefault(false)
        } ?: run {
            ddsWifiLockManager.release()
            null
        }
    }

    private fun stopDdsDiscovery() {
        ddsDiscoveryNativeBridge?.let { bridge ->
            runCatching { bridge.stop() }
                .onFailure { error -> Log.w(Tag, "Failed to stop DDS discovery monitor", error) }
        }
        ddsWifiLockManager.release()
    }

    private fun DdsDiscoverySnapshot.toParticipantEntities(
        sessionId: Long,
        sampledAt: Long,
    ): List<DdsParticipantSampleEntity> =
        participants.map { participant ->
            DdsParticipantSampleEntity(
                sessionId = sessionId,
                timestamp = sampledAt,
                participantGuid = participant.guid,
                participantName = participant.name,
                status = participant.status.name,
                firstSeenAt = participant.firstSeenAt,
                lastSeenAt = participant.lastSeenAt,
            )
        }

    private fun DdsDiscoverySnapshot.toEndpointEntities(
        sessionId: Long,
        sampledAt: Long,
    ): List<DdsEndpointSampleEntity> =
        endpoints.map { endpoint ->
            DdsEndpointSampleEntity(
                sessionId = sessionId,
                timestamp = sampledAt,
                endpointGuid = endpoint.guid,
                participantGuid = endpoint.participantGuid,
                topicName = endpoint.topicName,
                typeName = endpoint.typeName,
                kind = endpoint.kind.name,
                status = endpoint.status.name,
                firstSeenAt = endpoint.firstSeenAt,
                lastSeenAt = endpoint.lastSeenAt,
            )
        }

    private fun CoroutineScope.launchLoop(
        intervalMs: Long,
        block: suspend () -> Unit,
    ): Job = launch {
        while (isActive) {
            val startedAt = SystemRecorderClock.nowMillis()
            runCatching { block() }
                .onFailure { error -> Log.w(Tag, "Recording loop failed", error) }
            val elapsedMs = SystemRecorderClock.nowMillis() - startedAt
            delay((intervalMs - elapsedMs).coerceAtLeast(0L))
        }
    }

    private fun startRecorderForeground(sessionName: String) {
        val notification = RecorderNotification.build(
            context = this,
            sessionName = sessionName,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RecorderNotification.NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(RecorderNotification.NotificationId, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val Tag = "RecorderService"
        private const val ActionStart = "com.miyabi0619.radiofieldrecorder.action.START_RECORDING"
        private const val ActionStop = "com.miyabi0619.radiofieldrecorder.action.STOP_RECORDING"
        private const val ExtraSessionId = "session_id"
        private const val ExtraSessionName = "session_name"
        private const val MissingSessionId = -1L

        fun startIntent(
            context: Context,
            sessionId: Long,
            sessionName: String,
        ): Intent =
            Intent(context, RecorderService::class.java)
                .setAction(ActionStart)
                .putExtra(ExtraSessionId, sessionId)
                .putExtra(ExtraSessionName, sessionName)

        fun stopIntent(context: Context): Intent =
            Intent(context, RecorderService::class.java)
                .setAction(ActionStop)
    }
}
