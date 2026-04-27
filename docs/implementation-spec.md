# Radio Field Recorder Implementation Spec

## Scope

Radio Field Recorder records Wi-Fi state, HTTP/TCP reachability, and manual event markers on an Android device. The first MVP does not monitor ROS2, Fast DDS internals, DDS discovery, packet contents, Bluetooth payloads, or BLE surroundings.

The goal is to decide the next investigation target when a ROS2/Fast DDS/Bluetooth application becomes unstable:

- If Radio Field Recorder also shows Wi-Fi or IP failures, investigate radio, AP, host, firewall, or route first.
- If Radio Field Recorder remains healthy, investigate ROS2, DDS, QoS, domain ID, JNI/C++, or application logic first.

## Confirmed Decisions

- BLE is excluded from the MVP.
- A recording session is assumed to be shorter than 6 hours.
- Probe targets are explicitly typed as `HTTP` or `TCP`.
- Measurement intervals start as fixed defaults, with a settings screen added later.
- CSV export is manual. The app exports only when the user presses an export button.
- CSV export may contain multiple files, but a single user action should produce one export artifact.
- DDS support in MVP means DDS/RTPS helper metadata, not DDS protocol monitoring.

## Android Baseline

- Language: Kotlin
- UI: Jetpack Compose + Material3
- State: ViewModel + StateFlow
- Persistence: Room
- Settings: DataStore
- Background recording: Foreground Service
- Export: Storage Access Framework with a user-selected destination
- Current project baseline: `minSdk 31`, `targetSdk 36`, `compileSdk 36.1`

Foreground Service type for the MVP should start with `dataSync`, because the MVP records local samples and performs active probes for less than 6 hours. If BLE or external device interaction becomes part of recording, `connectedDevice` should be reconsidered with the relevant runtime permissions.

## MVP Features

### Session Management

Each session stores:

- `id`
- `name`
- `memo`
- `startedAt`
- `endedAt`
- `status`
- `wifiSampleIntervalMs`
- `probeIntervalMs`
- `probeTimeoutMs`

### Probe Targets

Each target stores:

- `id`
- `sessionId`
- `label`
- `type`: `HTTP` or `TCP`
- `address`
- `port`
- `path`
- `timeoutMs`

HTTP targets require an `http://` or `https://` URL.

TCP targets require a host or IP address and a port from `1..65535`.

### DDS Helper

The app does not open DDS participants or inspect DDS traffic. It can store ROS domain context and display RTPS default UDP port candidates to help users choose related probe targets or interpret network conditions.

Default RTPS port formula:

- `portBase = 7400`
- `domainIdGain = 250`
- `participantIdGain = 2`
- `builtinMulticastOffset = 0`
- `builtinUnicastOffset = 10`
- `userMulticastOffset = 1`
- `userUnicastOffset = 11`

For example, domain ID `0` and participant ID `0` produce `7400`, `7410`, `7401`, and `7411`.

UDP reachability is not treated as a success/failure probe in the MVP, because UDP does not provide TCP-like connect semantics. For reliable probing, use HTTP health endpoints or TCP ports.

### Wi-Fi Samples

The recorder periodically stores the currently connected Wi-Fi state:

- `timestamp`
- `ssid`
- `bssid`
- `rssi`
- `linkSpeedMbps`
- `frequencyMhz`
- `networkType`

The MVP does not perform high-frequency Wi-Fi scans.

### Probe Samples

For each HTTP/TCP target, the recorder periodically stores:

- `timestamp`
- `targetId`
- `targetLabel`
- `success`
- `latencyMs`
- `errorMessage`

HTTP uses GET. TCP uses socket connect.

### Event Markers

The recording screen provides preset event buttons:

- Delay
- Disconnect
- Recover
- BluetoothIssue
- Ros2Issue
- RobotStart
- ApChanged
- Memo

Each event stores:

- `timestamp`
- `type`
- `label`
- `memo`

### Summary

Session details display:

- Average successful latency
- Maximum successful latency
- p95 successful latency
- Probe failure rate
- Average Wi-Fi RSSI
- Minimum Wi-Fi RSSI
- Event list
- Time-ordered sample log

### Manual Export

The export button should create one user-selected artifact, preferably a zip file containing:

- `summary.csv`
- `wifi_samples.csv`
- `probe_samples.csv`
- `events.csv`

No automatic export is performed.

## Implementation Phases

### Phase 1: Spec and Core Logic

Status: completed.

Deliverables:

- Implementation spec in `docs/`
- Probe target validation and normalization
- DDS/RTPS port candidate calculation
- Session summary calculation
- CSV formatting helpers
- Unit tests for expected inputs and outputs

No Android service, Room, DataStore, or UI changes are included in this phase.

### Phase 2: Persistence and Settings

Status: completed.

Deliverables:

- Room entities and DAOs
- Repository layer
- DataStore settings for sample intervals and timeouts
- Unit tests for summary and export repository behavior where possible

Implemented notes:

- Room database version `1` stores sessions, HTTP/TCP targets, Wi-Fi samples, probe samples, and manual event markers.
- Session rows persist the recording intervals and optional ROS domain ID used at session start.
- Repository tests use fake DAOs to verify session creation, target insertion, sample insertion, and summary assembly without requiring a device.
- Room schema export is enabled under `app/schemas/`.

### Phase 3: Recorder Runtime

Status: next phase.

Deliverables:

- Foreground `RecorderService`
- Notification channel and ongoing notification
- Wi-Fi monitor
- HTTP/TCP probe runner
- Session start/stop flow
- Runtime permission handling for Wi-Fi-visible fields

### Phase 4: Compose UI

Deliverables:

- Session list screen
- Recording screen
- Session detail screen
- Settings screen
- Manual event marker input
- Manual export action through Storage Access Framework

### Phase 5: Optional Enhancements

Deliverables:

- BLE surroundings monitor
- Specific BLE target tracking
- Markdown report export
- External app event receiver
- Graph display
- Simple diagnostic comments

## Test Policy

Tests should first cover deterministic logic:

- TCP target validation rejects missing or invalid ports.
- HTTP target validation rejects non-HTTP schemes.
- DDS port calculation matches expected RTPS candidates.
- Summary calculation produces expected average, maximum, p95, failure rate, and RSSI values.
- CSV formatter escapes commas, quotes, and newlines correctly.

Android integration tests should be added after Room, service, and UI behavior exist.
