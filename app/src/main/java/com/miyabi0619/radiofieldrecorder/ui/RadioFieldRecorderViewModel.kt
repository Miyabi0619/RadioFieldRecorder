package com.miyabi0619.radiofieldrecorder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyabi0619.radiofieldrecorder.core.ProbeTargetParseResult
import com.miyabi0619.radiofieldrecorder.core.ProbeTargetParser
import com.miyabi0619.radiofieldrecorder.data.local.RadioFieldRecorderDatabase
import com.miyabi0619.radiofieldrecorder.data.local.SessionStatus
import com.miyabi0619.radiofieldrecorder.data.repository.CreateSessionRequest
import com.miyabi0619.radiofieldrecorder.data.repository.RecordingRepository
import com.miyabi0619.radiofieldrecorder.data.repository.SessionDetail
import com.miyabi0619.radiofieldrecorder.export.SessionExportArchive
import com.miyabi0619.radiofieldrecorder.export.SessionExportArchiveBuilder
import com.miyabi0619.radiofieldrecorder.recorder.RecorderServiceController
import com.miyabi0619.radiofieldrecorder.settings.RecorderSettings
import com.miyabi0619.radiofieldrecorder.settings.RecorderSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TargetInputType {
    HTTP,
    TCP,
}

data class StartRecordingInput(
    val sessionName: String,
    val memo: String,
    val targetType: TargetInputType,
    val targetLabel: String,
    val httpUrl: String,
    val tcpHost: String,
    val tcpPort: String,
    val rosDomainId: String,
)

class RadioFieldRecorderViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = RecordingRepository(
        RadioFieldRecorderDatabase.create(appContext),
    )
    private val settingsStore = RecorderSettingsStore(appContext)
    private val _selectedDetail = MutableStateFlow<SessionDetail?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private var pendingExport: SessionExportArchive? = null

    val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecorderSettings())

    val selectedDetail: StateFlow<SessionDetail?> = _selectedDetail.asStateFlow()
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            _selectedDetail.value = repository.getSessionDetail(sessionId)
        }
    }

    fun startRecording(input: StartRecordingInput) {
        viewModelScope.launch {
            val target = when (input.targetType) {
                TargetInputType.HTTP -> ProbeTargetParser.parseHttp(
                    label = input.targetLabel,
                    url = input.httpUrl,
                    timeoutMs = settings.value.probeTimeoutMs.toInt(),
                )
                TargetInputType.TCP -> ProbeTargetParser.parseTcp(
                    label = input.targetLabel,
                    host = input.tcpHost,
                    port = input.tcpPort.toIntOrNull(),
                    timeoutMs = settings.value.probeTimeoutMs.toInt(),
                )
            }
            if (target is ProbeTargetParseResult.Error) {
                _message.value = target.message
                return@launch
            }

            runCatching {
                val sessionName = input.sessionName.trim().ifBlank { "フィールド記録" }
                val sessionId = repository.createSession(
                    CreateSessionRequest(
                        name = sessionName,
                        memo = input.memo,
                        targets = listOf((target as ProbeTargetParseResult.Success).target),
                        settings = settings.value,
                        rosDomainId = input.rosDomainId.toIntOrNull(),
                        startedAt = System.currentTimeMillis(),
                    ),
                )
                RecorderServiceController.start(
                    context = appContext,
                    sessionId = sessionId,
                    sessionName = sessionName,
                )
                _selectedDetail.value = repository.getSessionDetail(sessionId)
                _message.value = "記録を開始しました。"
            }.onFailure { error ->
                _message.value = error.message ?: "記録を開始できませんでした。"
            }
        }
    }

    fun stopRecording(sessionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.stopSession(sessionId, System.currentTimeMillis())
                RecorderServiceController.stop(appContext)
                selectSession(sessionId)
                _message.value = "記録を停止しました。"
            }.onFailure { error ->
                _message.value = error.message ?: "記録を停止できませんでした。"
            }
        }
    }

    fun addEvent(
        sessionId: Long,
        type: String,
        memo: String?,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addEvent(
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    label = type,
                    memo = memo,
                )
                _selectedDetail.value = repository.getSessionDetail(sessionId)
            }.onFailure { error ->
                _message.value = error.message ?: "イベントを追加できませんでした。"
            }
        }
    }

    fun updateSettings(
        wifiIntervalMs: String,
        probeIntervalMs: String,
        probeTimeoutMs: String,
    ) {
        viewModelScope.launch {
            runCatching {
                settingsStore.update(
                    RecorderSettings(
                        wifiSampleIntervalMs = wifiIntervalMs.toLongOrNull()
                            ?: error("Wi-Fi記録間隔は数値で入力してください。"),
                        probeIntervalMs = probeIntervalMs.toLongOrNull()
                            ?: error("疎通確認間隔は数値で入力してください。"),
                        probeTimeoutMs = probeTimeoutMs.toLongOrNull()
                            ?: error("疎通確認タイムアウトは数値で入力してください。"),
                    ),
                )
                _message.value = "設定を保存しました。"
            }.onFailure { error ->
                _message.value = error.message ?: "設定を保存できませんでした。"
            }
        }
    }

    fun prepareExport(
        sessionId: Long,
        onPrepared: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val detail = repository.getSessionDetail(sessionId)
                    ?: error("セッションが見つかりません。")
                pendingExport = SessionExportArchiveBuilder.build(detail)
                onPrepared(requireNotNull(pendingExport).fileName)
            }.onFailure { error ->
                _message.value = error.message ?: "エクスポートを準備できませんでした。"
            }
        }
    }

    fun writePendingExport(uri: Uri?) {
        if (uri == null) {
            pendingExport = null
            return
        }

        viewModelScope.launch {
            runCatching {
                val export = pendingExport ?: error("保留中のエクスポートがありません。")
                appContext.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(export.bytes)
                } ?: error("出力先を開けませんでした。")
                pendingExport = null
                _message.value = "エクスポートが完了しました。"
            }.onFailure { error ->
                _message.value = error.message ?: "エクスポートを書き込めませんでした。"
            }
        }
    }

    fun runningSessionId(): Long? =
        sessions.value.firstOrNull { it.status == SessionStatus.RUNNING }?.id
}
