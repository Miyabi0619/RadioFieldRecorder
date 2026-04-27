package com.miyabi0619.radiofieldrecorder.recorder

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiMonitorTest {
    @Test
    fun cleanWifiValue_removesQuotesAndUnknownValues() {
        assertEquals("Lab WiFi", "\"Lab WiFi\"".cleanWifiValue())
        assertEquals(null, "<unknown ssid>".cleanWifiValue())
        assertEquals(null, "02:00:00:00:00:00".cleanWifiValue())
        assertEquals(null, "   ".cleanWifiValue())
    }

    @Test
    fun toEntity_keepsWifiSnapshotFields() {
        val entity = WifiSnapshot(
            timestamp = 10L,
            ssid = "Lab",
            bssid = "aa:bb",
            rssi = -55,
            linkSpeedMbps = 866,
            frequencyMhz = 5200,
            networkType = "WIFI",
        ).toEntity(sessionId = 2L)

        assertEquals(2L, entity.sessionId)
        assertEquals(10L, entity.timestamp)
        assertEquals("Lab", entity.ssid)
        assertEquals("aa:bb", entity.bssid)
        assertEquals(-55, entity.rssi)
        assertEquals(866, entity.linkSpeedMbps)
        assertEquals(5200, entity.frequencyMhz)
        assertEquals("WIFI", entity.networkType)
    }
}
