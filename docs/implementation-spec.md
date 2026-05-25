# Radio Field Recorder Implementation Spec

## Scope

Radio Field Recorder is an Android "drive recorder" for ROS2 field communication.

The app records what the Android device could observe around the time a ROS2 robot
or controller became unstable. The primary target is not generic HTTP/TCP reachability,
but ROS2/DDS discovery visibility between the Android device and the ROS2 PC.

The current direction is:

- Record Wi-Fi state over time.
- Join the configured ROS2 DDS domain without publishing robot-control data.
- Record discovered remote DDS participants.
- Record discovered remote topic endpoints, meaning remote readers and writers.
- Record manual event markers such as delay, disconnect, recovery, ROS2 issue, and memo.
- Export a time-aligned artifact for later investigation.

The app must not publish to robot-control topics as part of health checking. A successful
publish is not used as the main communication check, because it can accidentally affect
the robot and does not prove that the remote application processed the sample.

## Goal

When a ROS2/Fast DDS/Bluetooth application becomes unstable, the user should be able to
look back and decide the next investigation target:

- If Wi-Fi state is poor and DDS participants/endpoints disappear, investigate radio,
  AP, route, host, or firewall conditions first.
- If Wi-Fi looks healthy but DDS participants or expected topic endpoints disappear,
  investigate ROS2 domain, DDS discovery, multicast/unicast transport, QoS, or host-side
  DDS configuration.
- If Wi-Fi and DDS discovery remain healthy, investigate topic payload handling, QoS
  compatibility at the application level, JNI/C++, Bluetooth, or application logic.

This app is a recorder and diagnostic aid. It is not a complete ROS2 graph inspector and
does not prove that every ROS2 message payload is semantically valid.

## Confirmed Decisions

- BLE surroundings monitoring is excluded from the primary MVP.
- A recording session is assumed to be shorter than 6 hours.
- The primary ROS2 check is DDS discovery visibility, not HTTP or TCP probing.
- The app should not publish robot-control data for health checks.
- Level 1 and Level 2 DDS checks are in scope:
  - Level 1: remote DDS participant detection.
  - Level 2: remote topic endpoint detection.
- Level 3 publish/ack round-trip is out of scope for now.
- HTTP/TCP probes may remain as optional auxiliary diagnostics, but they are not the main
  ROS2 communication judgment.
- CSV export is manual. The app exports only when the user presses an export button.
- CSV export may contain multiple files, but one user action should produce one export
  artifact, preferably a zip file.

## Android Baseline

- Language: Kotlin
- Native communication layer: C++ + JNI + Fast DDS prefab
- UI: Jetpack Compose + Material3
- State: ViewModel + StateFlow
- Persistence: Room
- Settings: DataStore
- Background recording: Foreground Service
- Export: Storage Access Framework with a user-selected destination
- Current project baseline: `minSdk 31`, `targetSdk 36`, `compileSdk 36.1`

Foreground Service type for the recorder should start with `dataSync`, because the app
records local samples and DDS discovery metadata for less than 6 hours. If BLE or direct
external device interaction becomes part of recording, `connectedDevice` should be
reconsidered with the relevant runtime permissions.

## Native DDS Strategy

The app should use the Fast DDS prefab dependency from this repository's `local-maven/`
directory. Other local checkout paths must not be required for a clean build after
cloning this repository.

`/Users/soraha/StudioProjects/RC26_R1_Controller` may be used only as a reference
implementation while developing, not as a build-time dependency.

Expected reuse:

- `io.github.eyr1n:fastdds-prefab:2.13.4.3`
- Repository-local Maven artifact under `local-maven/`
- Gradle `prefab = true`
- CMake / NDK / ABI / linker flag setup
- `DomainParticipant` creation pattern
- JNI bridge pattern between Kotlin and C++

Expected new implementation:

- App-specific JNI function names.
- A `DdsDiscoveryMonitor` native component.
- A Kotlin `DdsDiscoveryNativeBridge` or equivalent wrapper.
- Room entities for DDS participant and endpoint observations.
- Recorder integration that starts/stops DDS discovery monitoring per session.

Generated ROS2 message `PubSubTypes` are not required for Level 1 and Level 2 monitoring.
The app observes DDS discovery metadata and does not deserialize topic payloads.

Generated message types become necessary only if a future phase subscribes to payloads or
publishes diagnostic samples.

## DDS Discovery Semantics

The DDS monitor creates a local `DomainParticipant` in the configured `ROS_DOMAIN_ID`.

This is not fully passive packet sniffing. The Android app will participate in DDS
discovery and emit normal discovery traffic. However, it must not create data writers for
robot-control topics or publish control samples.

The monitor should record:

- Local monitor start/stop time.
- ROS domain ID.
- Remote participant discovery events.
- Remote participant removal/loss events when available.
- Remote data writer discovery events.
- Remote data reader discovery events.
- Topic name.
- Type name.
- Endpoint kind: writer or reader.
- Remote GUID or stable identifier if available.
- QoS metadata when available and cheap to capture.
- First seen timestamp.
- Last seen timestamp.
- Current visible/lost status.

The Level 1 communication signal is:

- At least one expected remote participant is visible, or
- At least one remote participant is visible when no expected participant filter is set.

The Level 2 communication signal is:

- Expected topic endpoints are visible for the configured topic names, or
- Remote topic endpoints are being discovered when no expected topic filter is set.

The app should label these signals as "DDS discovery visibility" or "DDS endpoint
visibility", not as "message delivery success".

## Expected Topic Configuration

The app should allow a session to optionally store expected topic names.

Each expected topic stores:

- `id`
- `sessionId`
- `topicName`
- `expectedKind`: `WRITER`, `READER`, or `ANY`
- `typeName`, optional
- `required`, boolean
- `memo`, optional

The app should not publish test messages to expected topics.

Topic name handling should respect ROS2 DDS naming as observed by Fast DDS. For example,
ROS2 topic `/foo` may appear as DDS topic `rt/foo`. The UI should help the user avoid
confusing ROS topic names and DDS topic names.

## Session Management

Each session stores:

- `id`
- `name`
- `memo`
- `startedAt`
- `endedAt`
- `status`
- `rosDomainId`
- `wifiSampleIntervalMs`
- `ddsSnapshotIntervalMs`
- `discoveryLeaseDurationMs`, optional
- `expectedParticipant`, optional

Existing HTTP/TCP interval fields may remain for compatibility, but the new primary
recorder settings should be named around DDS discovery.

## Wi-Fi Samples

The recorder periodically stores the currently connected network state:

- `timestamp`
- `ssid`
- `bssid`
- `rssi`
- `linkSpeedMbps`
- `frequencyMhz`
- `networkType`

The MVP does not perform high-frequency Wi-Fi scans.

## DDS Participant Samples

For each discovery snapshot or event, the recorder stores participant visibility:

- `timestamp`
- `sessionId`
- `participantKey` or `guid`
- `participantName`, optional
- `hostInfo`, optional if available
- `status`: `VISIBLE`, `LOST`, or `UNKNOWN`
- `firstSeenAt`
- `lastSeenAt`
- `rawSummary`, optional diagnostic text

## DDS Endpoint Samples

For each discovered topic endpoint, the recorder stores:

- `timestamp`
- `sessionId`
- `endpointKey` or `guid`
- `participantKey`, optional
- `topicName`
- `typeName`
- `kind`: `WRITER` or `READER`
- `status`: `VISIBLE`, `LOST`, or `UNKNOWN`
- `firstSeenAt`
- `lastSeenAt`
- `qosSummary`, optional

## Auxiliary HTTP/TCP Probes

HTTP/TCP probes are optional auxiliary diagnostics.

They may be useful for:

- Checking SSH or another known TCP service.
- Checking a deliberately provided HTTP health endpoint.
- Separating basic IP reachability problems from DDS discovery problems.

They are not sufficient to prove ROS2 communication health.

TCP probing requires a host and port because TCP connect semantics always target a
specific socket endpoint. `ROS_DOMAIN_ID` does not define a TCP port for normal ROS2 DDS
communication.

## Event Markers

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

## Summary

Session details should display:

- DDS participant visibility over time.
- Expected participant visible ratio.
- DDS endpoint visibility over time.
- Expected topic endpoint visible ratio.
- Participant lost/recovered event list.
- Endpoint lost/recovered event list.
- Average Wi-Fi RSSI.
- Minimum Wi-Fi RSSI.
- Optional auxiliary HTTP/TCP probe metrics.
- Manual event list.
- Time-ordered sample log.

Diagnostic comments should prefer DDS terminology:

- If expected participants disappear, mention DDS discovery or network reachability.
- If expected endpoints disappear while participants remain visible, mention topic,
  QoS, node lifecycle, or DDS endpoint creation.
- If Wi-Fi RSSI is weak around disappearance events, mention radio/AP conditions.
- If Wi-Fi and DDS discovery look stable, point investigation toward application,
  Bluetooth, payload handling, or robot-side logic.

## Manual Export

The export button should create one user-selected artifact, preferably a zip file
containing:

- `summary.csv`
- `wifi_samples.csv`
- `dds_participants.csv`
- `dds_endpoints.csv`
- `expected_topics.csv`
- `events.csv`
- `auxiliary_probe_samples.csv`, if HTTP/TCP probes are enabled
- `conditions.csv`

No automatic export is performed.

## Implementation Phases

### Phase 0: Existing HTTP/TCP MVP

Status: implemented before the DDS direction change.

Implemented notes:

- Room database version `1` stores sessions, HTTP/TCP targets, Wi-Fi samples, probe
  samples, and manual event markers.
- Foreground `RecorderService` records Wi-Fi samples and HTTP/TCP probe samples.
- Compose UI supports session creation, detail display, settings, event markers, and zip
  export.
- Runtime permission requirements are centralized in `RecorderPermissions`.
- Simple diagnostic comments and external app event receiver exist.

This phase is retained as a compatibility baseline, but it is no longer the main product
direction.

### Phase 1: DDS Discovery Design

Status: pending.

Deliverables:

- Finalize native monitor API shape.
- Define Room entities for DDS participant and endpoint samples.
- Define Kotlin models for discovery snapshots.
- Define UI labels that clearly distinguish discovery visibility from message delivery.
- Define export CSV columns for participants and endpoints.

### Phase 2: Native Fast DDS Discovery Monitor

Status: pending.

Deliverables:

- Add Fast DDS prefab dependency and CMake configuration to this app.
- Add `participant_manager.cpp/.h` or equivalent participant ownership helper.
- Add `dds_discovery_monitor.cpp/.h`.
- Implement native start/stop for a configured ROS domain ID.
- Implement participant discovery callbacks.
- Implement reader/writer endpoint discovery callbacks.
- Expose snapshots or callback events to Kotlin through JNI.
- Add basic native error reporting.

The monitor must not create robot-control publishers or publish samples.

### Phase 3: Persistence and Recorder Integration

Status: pending.

Deliverables:

- Add Room entities and DAOs for DDS participants, endpoints, and expected topics.
- Add repository methods for discovery event insertion and summary assembly.
- Start and stop the native DDS monitor from the foreground recorder.
- Periodically persist current DDS visibility snapshots.
- Preserve Wi-Fi sampling.
- Keep HTTP/TCP probes as optional auxiliary probes.

### Phase 4: Compose UI Rework

Status: pending.

Deliverables:

- Make `ROS_DOMAIN_ID` a primary session setting.
- Add expected participant/topic configuration.
- Show current DDS participant count and endpoint count.
- Show expected topic endpoint status.
- Move HTTP/TCP setup into an auxiliary diagnostics section.
- Update Japanese UI copy so TCP is not presented as ROS2 communication judgment.

### Phase 5: Export and Diagnostics Rework

Status: pending.

Deliverables:

- Export DDS participant and endpoint CSV files.
- Add DDS-oriented summary metrics.
- Add DDS-oriented diagnostic comments.
- Add time-ordered combined log that aligns Wi-Fi, DDS, auxiliary probes, and events.

### Future Optional Enhancements

Status: future.

Potential enhancements:

- Subscribe to selected safe read-only topics and record last receive time.
- Decode selected message payloads when generated `PubSubTypes` are available.
- Add graph display for participant/topic visibility.
- Add Markdown report export.
- Add BLE surroundings monitor.
- Add Level 3 diagnostic publish/ack only for explicitly safe diagnostic topics.

## Test Policy

Tests should first cover deterministic logic:

- DDS discovery snapshot merging updates first seen / last seen / visible status correctly.
- Expected topic matching handles ROS topic names and DDS topic names consistently.
- Session summary calculates participant visible ratio and endpoint visible ratio.
- Export builder includes DDS participant and endpoint CSV files.
- Existing CSV formatter escapes commas, quotes, and newlines correctly.
- Existing HTTP/TCP parser tests remain as auxiliary probe coverage.

Native integration tests should be added after the C++ monitor exists. Where full DDS
integration is difficult on CI, isolate Kotlin-side snapshot merging and persistence logic
with fake discovery event sources.
