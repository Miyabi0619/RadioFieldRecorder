package com.miyabi0619.radiofieldrecorder.core

object CsvFormatter {
    fun row(values: List<Any?>): String =
        values.joinToString(separator = ",") { value ->
            escape(value?.toString().orEmpty())
        }

    fun escape(value: String): String {
        val requiresQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!requiresQuotes) return value

        return buildString {
            append('"')
            value.forEach { char ->
                if (char == '"') append("\"\"") else append(char)
            }
            append('"')
        }
    }
}

object CsvExportContentBuilder {
    fun summaryCsv(summary: SessionSummary): String =
        buildCsv(
            CsvFormatter.row(listOf("metric", "value")),
            CsvFormatter.row(listOf("probeCount", summary.probeCount)),
            CsvFormatter.row(listOf("probeFailureRate", summary.probeFailureRate)),
            CsvFormatter.row(listOf("averageLatencyMs", summary.averageLatencyMs)),
            CsvFormatter.row(listOf("maxLatencyMs", summary.maxLatencyMs)),
            CsvFormatter.row(listOf("p95LatencyMs", summary.p95LatencyMs)),
            CsvFormatter.row(listOf("averageWifiRssi", summary.averageWifiRssi)),
            CsvFormatter.row(listOf("minWifiRssi", summary.minWifiRssi)),
            CsvFormatter.row(listOf("eventCount", summary.eventCount)),
        )

    fun probeSamplesCsv(samples: List<ProbeSampleSnapshot>): String =
        buildCsv(
            CsvFormatter.row(listOf("timestamp", "target", "success", "latencyMs", "errorMessage")),
            *samples.map { sample ->
                CsvFormatter.row(
                    listOf(
                        sample.timestamp,
                        sample.target,
                        sample.success,
                        sample.latencyMs,
                        sample.errorMessage,
                    ),
                )
            }.toTypedArray(),
        )

    fun wifiSamplesCsv(samples: List<WifiSampleSnapshot>): String =
        buildCsv(
            CsvFormatter.row(
                listOf(
                    "timestamp",
                    "ssid",
                    "bssid",
                    "rssi",
                    "linkSpeedMbps",
                    "frequencyMhz",
                ),
            ),
            *samples.map { sample ->
                CsvFormatter.row(
                    listOf(
                        sample.timestamp,
                        sample.ssid,
                        sample.bssid,
                        sample.rssi,
                        sample.linkSpeedMbps,
                        sample.frequencyMhz,
                    ),
                )
            }.toTypedArray(),
        )

    fun eventsCsv(events: List<EventMarkerSnapshot>): String =
        buildCsv(
            CsvFormatter.row(listOf("timestamp", "type", "label", "memo")),
            *events.map { event ->
                CsvFormatter.row(listOf(event.timestamp, event.type, event.label, event.memo))
            }.toTypedArray(),
        )

    private fun buildCsv(vararg rows: String): String =
        rows.joinToString(separator = "\n", postfix = "\n")
}
