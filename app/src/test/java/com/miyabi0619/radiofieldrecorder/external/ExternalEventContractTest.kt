package com.miyabi0619.radiofieldrecorder.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalEventContractTest {
    @Test
    fun parse_returnsNullWhenTypeIsMissing() {
        assertNull(
            ExternalEventContract.parse(
                source = "robot",
                type = " ",
                label = null,
                value = null,
                memo = null,
                timestamp = null,
            ),
        )
    }

    @Test
    fun parse_defaultsSourceAndLabel() {
        val payload = ExternalEventContract.parse(
            source = null,
            type = "bt_timeout",
            label = null,
            value = null,
            memo = null,
            timestamp = -1L,
        )

        requireNotNull(payload)
        assertEquals("external", payload.source)
        assertEquals("bt_timeout", payload.label)
        assertEquals("EXTERNAL_BT_TIMEOUT", payload.eventType())
        assertEquals(null, payload.timestamp)
    }

    @Test
    fun eventMemo_combinesSourceValueAndMemo() {
        val payload = ExternalEventPayload(
            source = "controller",
            type = "BT_ACK",
            label = "ack",
            value = "42ms",
            memo = "retry=1",
            timestamp = 100L,
        )

        assertEquals("source=controller | value=42ms | retry=1", payload.eventMemo())
    }
}
