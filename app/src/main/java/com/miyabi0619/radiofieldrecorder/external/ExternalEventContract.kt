package com.miyabi0619.radiofieldrecorder.external

data class ExternalEventPayload(
    val source: String,
    val type: String,
    val label: String,
    val value: String?,
    val memo: String?,
    val timestamp: Long?,
) {
    fun eventType(): String = "EXTERNAL_${type.uppercase()}"

    fun eventMemo(): String? {
        val parts = listOfNotNull(
            "source=$source",
            value?.takeIf { it.isNotBlank() }?.let { "value=$it" },
            memo?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(separator = " | ").ifBlank { null }
    }
}

object ExternalEventContract {
    const val Action = "com.miyabi0619.radiofieldrecorder.EXTERNAL_EVENT"
    const val ExtraSource = "source"
    const val ExtraType = "type"
    const val ExtraLabel = "label"
    const val ExtraValue = "value"
    const val ExtraMemo = "memo"
    const val ExtraTimestamp = "timestamp"

    fun parse(
        source: String?,
        type: String?,
        label: String?,
        value: String?,
        memo: String?,
        timestamp: Long?,
    ): ExternalEventPayload? {
        val normalizedType = type?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedSource = source?.trim()?.takeIf { it.isNotBlank() } ?: "external"
        val normalizedLabel = label?.trim()?.takeIf { it.isNotBlank() } ?: normalizedType

        return ExternalEventPayload(
            source = normalizedSource,
            type = normalizedType,
            label = normalizedLabel,
            value = value?.trim()?.takeIf { it.isNotBlank() },
            memo = memo?.trim()?.takeIf { it.isNotBlank() },
            timestamp = timestamp?.takeIf { it > 0L },
        )
    }
}
