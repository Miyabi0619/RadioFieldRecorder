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
            return ProbeTargetParseResult.Error("HTTP URLを入力してください。")
        }
        if (timeoutMs <= 0) {
            return ProbeTargetParseResult.Error("タイムアウトは1以上にしてください。")
        }

        val uri = runCatching { URI(trimmedUrl) }.getOrNull()
            ?: return ProbeTargetParseResult.Error("HTTP URLの形式が正しくありません。")

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return ProbeTargetParseResult.Error("HTTPターゲットは http または https を指定してください。")
        }
        if (uri.host.isNullOrBlank()) {
            return ProbeTargetParseResult.Error("HTTP URLのホストを入力してください。")
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
            return ProbeTargetParseResult.Error("TCPホストを入力してください。")
        }
        if (trimmedHost.contains("://")) {
            return ProbeTargetParseResult.Error("TCPホストにはURLスキームを含めないでください。")
        }
        if (port == null || port !in 1..65_535) {
            return ProbeTargetParseResult.Error("TCPポートは1〜65535で入力してください。")
        }
        if (timeoutMs <= 0) {
            return ProbeTargetParseResult.Error("タイムアウトは1以上にしてください。")
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
