package com.miyabi0619.radiofieldrecorder.export

import com.miyabi0619.radiofieldrecorder.core.CsvExportContentBuilder
import com.miyabi0619.radiofieldrecorder.core.CsvFormatter
import com.miyabi0619.radiofieldrecorder.core.EventMarkerSnapshot
import com.miyabi0619.radiofieldrecorder.core.ProbeSampleSnapshot
import com.miyabi0619.radiofieldrecorder.core.WifiSampleSnapshot
import com.miyabi0619.radiofieldrecorder.data.repository.SessionDetail
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SessionExportArchive(
    val fileName: String,
    val mimeType: String = "application/zip",
    val bytes: ByteArray,
)

object SessionExportArchiveBuilder {
    fun build(detail: SessionDetail): SessionExportArchive {
        val safeName = detail.session.name
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "session_${detail.session.id}" }
        val fileName = "radio_field_recorder_${detail.session.id}_$safeName.zip"
        val summary = detail.summary

        return SessionExportArchive(
            fileName = fileName,
            bytes = buildZip(
                "summary.csv" to CsvExportContentBuilder.summaryCsv(summary),
                "wifi_samples.csv" to CsvExportContentBuilder.wifiSamplesCsv(
                    detail.wifiSamples.map {
                        WifiSampleSnapshot(
                            timestamp = it.timestamp,
                            ssid = it.ssid,
                            bssid = it.bssid,
                            rssi = it.rssi,
                            linkSpeedMbps = it.linkSpeedMbps,
                            frequencyMhz = it.frequencyMhz,
                        )
                    },
                ),
                "probe_samples.csv" to CsvExportContentBuilder.probeSamplesCsv(
                    detail.probeSamples.map {
                        ProbeSampleSnapshot(
                            timestamp = it.timestamp,
                            target = it.targetLabel,
                            success = it.success,
                            latencyMs = it.latencyMs,
                            errorMessage = it.errorMessage,
                        )
                    },
                ),
                "events.csv" to CsvExportContentBuilder.eventsCsv(
                    detail.events.map {
                        EventMarkerSnapshot(
                            timestamp = it.timestamp,
                            type = it.type,
                            label = it.label,
                            memo = it.memo,
                        )
                    },
                ),
                "conditions.csv" to conditionsCsv(detail),
            ),
        )
    }

    private fun conditionsCsv(detail: SessionDetail): String =
        buildString {
            append(CsvFormatter.row(listOf("key", "value"))).append('\n')
            append(CsvFormatter.row(listOf("sessionId", detail.session.id))).append('\n')
            append(CsvFormatter.row(listOf("name", detail.session.name))).append('\n')
            append(CsvFormatter.row(listOf("startedAt", detail.session.startedAt))).append('\n')
            append(CsvFormatter.row(listOf("endedAt", detail.session.endedAt))).append('\n')
            append(CsvFormatter.row(listOf("wifiSampleIntervalMs", detail.session.wifiSampleIntervalMs))).append('\n')
            append(CsvFormatter.row(listOf("probeIntervalMs", detail.session.probeIntervalMs))).append('\n')
            append(CsvFormatter.row(listOf("probeTimeoutMs", detail.session.probeTimeoutMs))).append('\n')
            append(CsvFormatter.row(listOf("rosDomainId", detail.session.rosDomainId))).append('\n')
            detail.targets.forEachIndexed { index, target ->
                append(
                    CsvFormatter.row(
                        listOf(
                            "target${index + 1}",
                            "${target.label} ${target.type} ${target.address}",
                        ),
                    ),
                ).append('\n')
            }
        }

    private fun buildZip(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
