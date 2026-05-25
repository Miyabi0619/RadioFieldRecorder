package com.miyabi0619.radiofieldrecorder.dds

import org.json.JSONObject

enum class DdsVisibilityStatus {
    VISIBLE,
    LOST,
    IGNORED,
    UNKNOWN,
}

enum class DdsEndpointKind {
    WRITER,
    READER,
}

data class DdsParticipantSnapshot(
    val guid: String,
    val name: String?,
    val status: DdsVisibilityStatus,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)

data class DdsEndpointSnapshot(
    val guid: String,
    val participantGuid: String?,
    val topicName: String,
    val typeName: String,
    val kind: DdsEndpointKind,
    val status: DdsVisibilityStatus,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)

data class DdsDiscoverySnapshot(
    val domainId: Int,
    val startedAt: Long,
    val participants: List<DdsParticipantSnapshot>,
    val endpoints: List<DdsEndpointSnapshot>,
)

object DdsDiscoverySnapshotParser {
    fun parse(json: String): DdsDiscoverySnapshot {
        val root = JSONObject(json)
        val participantsJson = root.getJSONArray("participants")
        val endpointsJson = root.getJSONArray("endpoints")

        return DdsDiscoverySnapshot(
            domainId = root.getInt("domainId"),
            startedAt = root.getLong("startedAt"),
            participants = buildList {
                for (index in 0 until participantsJson.length()) {
                    val item = participantsJson.getJSONObject(index)
                    add(
                        DdsParticipantSnapshot(
                            guid = item.getString("guid"),
                            name = item.optString("name").takeIf { it.isNotBlank() },
                            status = parseVisibilityStatus(item.optString("status")),
                            firstSeenAt = item.getLong("firstSeenAt"),
                            lastSeenAt = item.getLong("lastSeenAt"),
                        ),
                    )
                }
            },
            endpoints = buildList {
                for (index in 0 until endpointsJson.length()) {
                    val item = endpointsJson.getJSONObject(index)
                    add(
                        DdsEndpointSnapshot(
                            guid = item.getString("guid"),
                            participantGuid = item.optString("participantGuid").takeIf { it.isNotBlank() },
                            topicName = item.getString("topicName"),
                            typeName = item.getString("typeName"),
                            kind = parseEndpointKind(item.optString("kind")),
                            status = parseVisibilityStatus(item.optString("status")),
                            firstSeenAt = item.getLong("firstSeenAt"),
                            lastSeenAt = item.getLong("lastSeenAt"),
                        ),
                    )
                }
            },
        )
    }

    private fun parseVisibilityStatus(value: String): DdsVisibilityStatus =
        DdsVisibilityStatus.entries.firstOrNull { it.name == value }
            ?: DdsVisibilityStatus.UNKNOWN

    private fun parseEndpointKind(value: String): DdsEndpointKind =
        DdsEndpointKind.entries.firstOrNull { it.name == value }
            ?: DdsEndpointKind.READER
}
