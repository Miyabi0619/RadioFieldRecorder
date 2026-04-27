package com.miyabi0619.radiofieldrecorder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeTargetParserTest {
    @Test
    fun parseHttp_acceptsHttpUrlAndNormalizesPath() {
        val result = ProbeTargetParser.parseHttp(
            label = "Health",
            url = "http://192.168.1.30:8080/health",
            timeoutMs = 1_500,
        )

        assertTrue(result is ProbeTargetParseResult.Success)
        val target = (result as ProbeTargetParseResult.Success).target
        assertEquals("Health", target.label)
        assertEquals(ProbeTargetType.HTTP, target.type)
        assertEquals("http://192.168.1.30:8080/health", target.address)
        assertEquals(8080, target.port)
        assertEquals("/health", target.path)
        assertEquals(1_500, target.timeoutMs)
    }

    @Test
    fun parseHttp_rejectsNonHttpScheme() {
        val result = ProbeTargetParser.parseHttp(
            label = "",
            url = "ftp://192.168.1.30/health",
        )

        assertTrue(result is ProbeTargetParseResult.Error)
    }

    @Test
    fun parseTcp_acceptsHostAndPort() {
        val result = ProbeTargetParser.parseTcp(
            label = "",
            host = "192.168.1.30",
            port = 7400,
        )

        assertTrue(result is ProbeTargetParseResult.Success)
        val target = (result as ProbeTargetParseResult.Success).target
        assertEquals("192.168.1.30", target.label)
        assertEquals(ProbeTargetType.TCP, target.type)
        assertEquals("192.168.1.30", target.address)
        assertEquals(7400, target.port)
    }

    @Test
    fun parseTcp_rejectsMissingPort() {
        val result = ProbeTargetParser.parseTcp(
            label = "DDS",
            host = "192.168.1.30",
            port = null,
        )

        assertTrue(result is ProbeTargetParseResult.Error)
    }
}
