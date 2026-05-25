package com.miyabi0619.radiofieldrecorder.recorder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.miyabi0619.radiofieldrecorder.data.local.RadioFieldRecorderDatabase
import com.miyabi0619.radiofieldrecorder.data.repository.RecordingRepository
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordingJob: Job? = null
    private var activeSessionId: Long? = null

    override fun onCreate() {
        super.onCreate()
        val database = RadioFieldRecorderDatabase.create(this)
        repository = RecordingRepository(database)
        wifiMonitor = WifiMonitor(this)
        networkProbeRunner = NetworkProbeRunner()
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
