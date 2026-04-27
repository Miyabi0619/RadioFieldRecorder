package com.miyabi0619.radiofieldrecorder.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvFormatterTest {
    @Test
    fun row_escapesCommaQuoteAndNewline() {
        val row = CsvFormatter.row(
            listOf(
                1000L,
                "target,with,comma",
                "quote \"value\"",
                "line1\nline2",
                null,
            ),
        )

        assertEquals(
            "1000,\"target,with,comma\",\"quote \"\"value\"\"\",\"line1\nline2\",",
            row,
        )
    }

    @Test
    fun probeSamplesCsv_outputsHeaderAndRows() {
        val csv = CsvExportContentBuilder.probeSamplesCsv(
            listOf(
                ProbeSampleSnapshot(
                    timestamp = 123L,
                    target = "ROS2 PC",
                    success = true,
                    latencyMs = 12L,
                    errorMessage = null,
                ),
            ),
        )

        assertEquals(
            "timestamp,target,success,latencyMs,errorMessage\n" +
                "123,ROS2 PC,true,12,\n",
            csv,
        )
    }
}
