package com.miyabi0619.radiofieldrecorder.core

import java.net.URI

enum class ProbeTargetType {
    HTTP,
    TCP,
}

data class ProbeTarget(
    val label: String,
    val type: ProbeTargetType,
    val address: String,
    val port: Int?,
    val path: String?,
    val timeoutMs: Int,
)

sealed class ProbeTargetParseResult {
    data class Success(val target: ProbeTarget) : ProbeTargetParseResult()
    data class Error(val message: String) : ProbeTargetParseResult()
}

object ProbeTargetParser {
    private const val DefaultTimeoutMs = 1_000

    fun parseHttp(
        label: String,
        url: String,
        timeoutMs: Int = DefaultTimeoutMs,
    ): ProbeTargetParseResult {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return ProbeTargetParseResult.Error("HTTP target URL is required.")
        }
        if (timeoutMs <= 0) {
            return ProbeTargetParseResult.Error("Timeout must be greater than 0.")
        }

        val uri = runCatching { URI(trimmedUrl) }.getOrNull()
            ?: return ProbeTargetParseResult.Error("HTTP target URL is invalid.")

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return ProbeTargetParseResult.Error("HTTP target must use http or https.")
        }
        if (uri.host.isNullOrBlank()) {
            return ProbeTargetParseResult.Error("HTTP target host is required.")
        }

        return ProbeTargetParseResult.Success(
            ProbeTarget(
                label = normalizedLabel(label, uri.host),
                type = ProbeTargetType.HTTP,
                address = uri.toASCIIString(),
                port = uri.port.takeIf { it > 0 },
                path = uri.rawPath.takeUnless { it.isNullOrBlank() } ?: "/",
                timeoutMs = timeoutMs,
            ),
        )
    }

    fun parseTcp(
        label: String,
        host: String,
        port: Int?,
        timeoutMs: Int = DefaultTimeoutMs,
    ): ProbeTargetParseResult {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) {
            return ProbeTargetParseResult.Error("TCP target host is required.")
        }
        if (trimmedHost.contains("://")) {
            return ProbeTargetParseResult.Error("TCP target host must not include a URL scheme.")
        }
        if (port == null || port !in 1..65_535) {
            return ProbeTargetParseResult.Error("TCP target port must be between 1 and 65535.")
        }
        if (timeoutMs <= 0) {
            return ProbeTargetParseResult.Error("Timeout must be greater than 0.")
        }

        return ProbeTargetParseResult.Success(
            ProbeTarget(
                label = normalizedLabel(label, trimmedHost),
                type = ProbeTargetType.TCP,
                address = trimmedHost,
                port = port,
                path = null,
                timeoutMs = timeoutMs,
            ),
        )
    }

    private fun normalizedLabel(label: String, fallback: String): String =
        label.trim().ifBlank { fallback }
}
