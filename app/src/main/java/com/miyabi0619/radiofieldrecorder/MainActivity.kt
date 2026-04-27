package com.miyabi0619.radiofieldrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miyabi0619.radiofieldrecorder.data.local.SessionEntity
import com.miyabi0619.radiofieldrecorder.data.local.SessionStatus
import com.miyabi0619.radiofieldrecorder.data.repository.SessionDetail
import com.miyabi0619.radiofieldrecorder.diagnostics.DiagnosticCommentGenerator
import com.miyabi0619.radiofieldrecorder.recorder.RecorderPermissions
import com.miyabi0619.radiofieldrecorder.settings.RecorderSettings
import com.miyabi0619.radiofieldrecorder.ui.RadioFieldRecorderViewModel
import com.miyabi0619.radiofieldrecorder.ui.StartRecordingInput
import com.miyabi0619.radiofieldrecorder.ui.TargetInputType
import com.miyabi0619.radiofieldrecorder.ui.theme.RadioFieldRecorderTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: RadioFieldRecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip"),
            ) { uri ->
                viewModel.writePendingExport(uri)
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {}

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    RecorderPermissions.runtimePermissions().toTypedArray(),
                )
            }

            RadioFieldRecorderTheme {
                RadioFieldRecorderApp(
                    viewModel = viewModel,
                    onExportPrepared = { fileName -> exportLauncher.launch(fileName) },
                )
            }
        }
    }
}

@Composable
fun RadioFieldRecorderApp(
    viewModel: RadioFieldRecorderViewModel,
    onExportPrepared: (String) -> Unit,
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.SESSIONS) }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.selectedDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        val currentMessage = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentMessage)
        viewModel.clearMessage()
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            when (currentDestination) {
                AppDestination.SESSIONS -> SessionsScreen(
                    sessions = sessions,
                    onStart = viewModel::startRecording,
                    onSelect = { sessionId ->
                        viewModel.selectSession(sessionId)
                        currentDestination = AppDestination.DETAIL
                    },
                    onStop = viewModel::stopRecording,
                    modifier = Modifier.padding(innerPadding),
                )
                AppDestination.DETAIL -> DetailScreen(
                    detail = selectedDetail,
                    onStop = viewModel::stopRecording,
                    onAddEvent = viewModel::addEvent,
                    onExport = { sessionId ->
                        scope.launch {
                            viewModel.prepareExport(sessionId, onExportPrepared)
                        }
                    },
                    modifier = Modifier.padding(innerPadding),
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onSave = viewModel::updateSettings,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

enum class AppDestination(
    val label: String,
    val icon: ImageVector,
) {
    SESSIONS("Sessions", Icons.Default.Home),
    DETAIL("Detail", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.AccountBox),
}

@Composable
private fun SessionsScreen(
    sessions: List<SessionEntity>,
    onStart: (StartRecordingInput) -> Unit,
    onSelect: (Long) -> Unit,
    onStop: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Radio Field Recorder", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Record Wi-Fi state, HTTP/TCP reachability, and manual events.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            NewSessionCard(onStart = onStart)
        }
        items(sessions, key = { it.id }) { session ->
            SessionRow(
                session = session,
                onSelect = { onSelect(session.id) },
                onStop = { onStop(session.id) },
            )
        }
    }
}

@Composable
private fun NewSessionCard(
    onStart: (StartRecordingInput) -> Unit,
) {
    var sessionName by rememberSaveable { mutableStateOf("Field session") }
    var memo by rememberSaveable { mutableStateOf("") }
    var targetType by rememberSaveable { mutableStateOf(TargetInputType.HTTP) }
    var targetLabel by rememberSaveable { mutableStateOf("ROS2 PC") }
    var httpUrl by rememberSaveable { mutableStateOf("http://192.168.1.30:8080/health") }
    var tcpHost by rememberSaveable { mutableStateOf("192.168.1.30") }
    var tcpPort by rememberSaveable { mutableStateOf("7411") }
    var rosDomainId by rememberSaveable { mutableStateOf("0") }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("New recording", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                label = { Text("Session name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("Memo") },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = targetType == TargetInputType.HTTP,
                    onClick = { targetType = TargetInputType.HTTP },
                    label = { Text("HTTP") },
                )
                FilterChip(
                    selected = targetType == TargetInputType.TCP,
                    onClick = { targetType = TargetInputType.TCP },
                    label = { Text("TCP") },
                )
            }
            OutlinedTextField(
                value = targetLabel,
                onValueChange = { targetLabel = it },
                label = { Text("Target label") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (targetType == TargetInputType.HTTP) {
                OutlinedTextField(
                    value = httpUrl,
                    onValueChange = { httpUrl = it },
                    label = { Text("HTTP URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tcpHost,
                        onValueChange = { tcpHost = it },
                        label = { Text("TCP host") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tcpPort,
                        onValueChange = { tcpPort = it },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.55f),
                    )
                }
            }
            OutlinedTextField(
                value = rosDomainId,
                onValueChange = { rosDomainId = it },
                label = { Text("ROS_DOMAIN_ID") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onStart(
                        StartRecordingInput(
                            sessionName = sessionName,
                            memo = memo,
                            targetType = targetType,
                            targetLabel = targetLabel,
                            httpUrl = httpUrl,
                            tcpHost = tcpHost,
                            tcpPort = tcpPort,
                            rosDomainId = rosDomainId,
                        ),
                    )
                },
            ) {
                Text("Start recording")
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionEntity,
    onSelect: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(session.name, style = MaterialTheme.typography.titleMedium)
                Text(session.status.name, style = MaterialTheme.typography.labelMedium)
            }
            Text("Started ${formatTime(session.startedAt)}")
            session.endedAt?.let { Text("Stopped ${formatTime(it)}") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelect) { Text("Open") }
                if (session.status == SessionStatus.RUNNING) {
                    TextButton(onClick = onStop) { Text("Stop") }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    detail: SessionDetail?,
    onStop: (Long) -> Unit,
    onAddEvent: (Long, String, String?) -> Unit,
    onExport: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (detail == null) {
        Column(modifier = modifier.padding(16.dp)) {
            Text("Select a session from Sessions.")
        }
        return
    }

    var eventMemo by rememberSaveable(detail.session.id) { mutableStateOf("") }
    val isRunning = detail.session.status == SessionStatus.RUNNING
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(detail.session.name, style = MaterialTheme.typography.headlineSmall)
            Text("Started ${formatTime(detail.session.startedAt)}")
            detail.session.endedAt?.let { Text("Stopped ${formatTime(it)}") }
            Text("Wi-Fi ${detail.session.wifiSampleIntervalMs}ms / Probe ${detail.session.probeIntervalMs}ms")
            detail.session.rosDomainId?.let { Text("ROS_DOMAIN_ID $it") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExport(detail.session.id) }) { Text("Export zip") }
                if (isRunning) {
                    Button(onClick = { onStop(detail.session.id) }) { Text("Stop") }
                }
            }
        }
        item {
            SummaryCard(detail)
        }
        item {
            EventMarkerCard(
                memo = eventMemo,
                onMemoChange = { eventMemo = it },
                enabled = isRunning,
                onAddEvent = { type ->
                    onAddEvent(detail.session.id, type, eventMemo)
                    eventMemo = ""
                },
            )
        }
        item {
            Text("Targets", style = MaterialTheme.typography.titleMedium)
            detail.targets.forEach { target ->
                Text("${target.label}: ${target.type} ${target.address}${target.port?.let { ":$it" } ?: ""}")
            }
        }
        item {
            Text("Events", style = MaterialTheme.typography.titleMedium)
            if (detail.events.isEmpty()) {
                Text("No events.")
            } else {
                detail.events.takeLast(20).forEach { event ->
                    Text("${formatTime(event.timestamp)} ${event.type} ${event.memo.orEmpty()}")
                }
            }
        }
        item {
            Text("Recent probes", style = MaterialTheme.typography.titleMedium)
            detail.probeSamples.takeLast(20).forEach { sample ->
                Text(
                    "${formatTime(sample.timestamp)} ${sample.targetLabel} " +
                        "${if (sample.success) "OK" else "NG"} " +
                        "${sample.latencyMs?.let { "${it}ms" } ?: sample.errorMessage.orEmpty()}",
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(detail: SessionDetail) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Summary", style = MaterialTheme.typography.titleMedium)
            Text("Probe count: ${detail.summary.probeCount}")
            Text("Failure rate: ${formatPercent(detail.summary.probeFailureRate)}")
            Text("Average latency: ${formatDouble(detail.summary.averageLatencyMs)} ms")
            Text("Max latency: ${detail.summary.maxLatencyMs ?: "-"} ms")
            Text("p95 latency: ${detail.summary.p95LatencyMs ?: "-"} ms")
            Text("Average RSSI: ${formatDouble(detail.summary.averageWifiRssi)} dBm")
            Text("Minimum RSSI: ${detail.summary.minWifiRssi ?: "-"} dBm")
            Text("Events: ${detail.summary.eventCount}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            DiagnosticCommentGenerator.generate(detail.summary).forEach { comment ->
                Text(comment, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EventMarkerCard(
    memo: String,
    onMemoChange: (String) -> Unit,
    enabled: Boolean,
    onAddEvent: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Event marker", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = memo,
                onValueChange = onMemoChange,
                label = { Text("Memo") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventTypes.forEach { type ->
                    Button(
                        onClick = { onAddEvent(type) },
                        enabled = enabled,
                    ) {
                        Text(type)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: RecorderSettings,
    onSave: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wifiInterval by remember(settings.wifiSampleIntervalMs) {
        mutableStateOf(settings.wifiSampleIntervalMs.toString())
    }
    var probeInterval by remember(settings.probeIntervalMs) {
        mutableStateOf(settings.probeIntervalMs.toString())
    }
    var probeTimeout by remember(settings.probeTimeoutMs) {
        mutableStateOf(settings.probeTimeoutMs.toString())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = wifiInterval,
            onValueChange = { wifiInterval = it },
            label = { Text("Wi-Fi sample interval ms") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = probeInterval,
            onValueChange = { probeInterval = it },
            label = { Text("Probe interval ms") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = probeTimeout,
            onValueChange = { probeTimeout = it },
            label = { Text("Probe timeout ms") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        Text("Defaults: Wi-Fi 1000ms, probe 2000ms, timeout 1000ms.")
        Button(
            onClick = { onSave(wifiInterval, probeInterval, probeTimeout) },
        ) {
            Text("Save settings")
        }
        Spacer(Modifier.height(8.dp))
        Text("Export is manual. The app writes a zip only when Export zip is pressed.")
    }
}

private val EventTypes = listOf(
    "Delay",
    "Disconnect",
    "Recover",
    "BluetoothIssue",
    "Ros2Issue",
    "RobotStart",
    "ApChanged",
    "Memo",
)

private val TimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private fun formatTime(timestamp: Long): String =
    TimeFormatter.format(Instant.ofEpochMilli(timestamp))

private fun formatPercent(value: Double?): String =
    value?.let { "%.1f%%".format(it * 100.0) } ?: "-"

private fun formatDouble(value: Double?): String =
    value?.let { "%.1f".format(it) } ?: "-"
