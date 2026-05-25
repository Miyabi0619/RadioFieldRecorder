package com.miyabi0619.radiofieldrecorder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SessionStatus {
    RUNNING,
    STOPPED,
}

enum class StoredProbeTargetType {
    HTTP,
    TCP,
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val memo: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus,
    val wifiSampleIntervalMs: Long,
    val probeIntervalMs: Long,
    val probeTimeoutMs: Long,
    val rosDomainId: Int?,
)

@Entity(
    tableName = "probe_targets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ProbeTargetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val label: String,
    val type: StoredProbeTargetType,
    val address: String,
    val port: Int?,
    val path: String?,
    val timeoutMs: Long,
)

@Entity(
    tableName = "wifi_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "timestamp"])],
)
data class WifiSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val timestamp: Long,
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val networkType: String?,
)

@Entity(
    tableName = "probe_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("targetId"),
        Index(value = ["sessionId", "timestamp"]),
    ],
)
data class ProbeSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val targetId: Long?,
    val timestamp: Long,
    val targetLabel: String,
    val success: Boolean,
    val latencyMs: Long?,
    val errorMessage: String?,
)

@Entity(
    tableName = "event_markers",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "timestamp"])],
)
data class EventMarkerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val timestamp: Long,
    val type: String,
    val label: String,
    val memo: String?,
)

@Entity(
    tableName = "dds_participant_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "timestamp"]),
        Index(value = ["sessionId", "participantGuid"]),
    ],
)
data class DdsParticipantSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val timestamp: Long,
    val participantGuid: String,
    val participantName: String?,
    val status: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)

@Entity(
    tableName = "dds_endpoint_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "timestamp"]),
        Index(value = ["sessionId", "endpointGuid"]),
        Index(value = ["sessionId", "topicName"]),
    ],
)
data class DdsEndpointSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val timestamp: Long,
    val endpointGuid: String,
    val participantGuid: String?,
    val topicName: String,
    val typeName: String,
    val kind: String,
    val status: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)
