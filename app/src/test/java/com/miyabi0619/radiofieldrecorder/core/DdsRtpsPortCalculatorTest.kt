package com.miyabi0619.radiofieldrecorder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DdsRtpsPortCalculatorTest {
    @Test
    fun candidates_domainZeroParticipantZeroMatchDefaultRtpsPorts() {
        val candidates = DdsRtpsPortCalculator.candidates(domainId = 0, participantId = 0)

        assertEquals(
            listOf(7400, 7410, 7401, 7411),
            candidates.map { it.port },
        )
        assertEquals(
            listOf("builtin_multicast", "builtin_unicast", "user_multicast", "user_unicast"),
            candidates.map { it.name },
        )
    }

    @Test
    fun candidates_applyDomainAndParticipantOffset() {
        val candidates = DdsRtpsPortCalculator.candidates(domainId = 2, participantId = 3)

        assertEquals(
            listOf(7900, 7916, 7901, 7917),
            candidates.map { it.port },
        )
    }

    @Test
    fun candidates_rejectNegativeDomainId() {
        assertThrows(IllegalArgumentException::class.java) {
            DdsRtpsPortCalculator.candidates(domainId = -1)
        }
    }
}
