package com.miyabi0619.radiofieldrecorder.core

data class DdsRtpsPortCandidate(
    val name: String,
    val port: Int,
    val description: String,
)

object DdsRtpsPortCalculator {
    private const val PortBase = 7_400
    private const val DomainIdGain = 250
    private const val ParticipantIdGain = 2
    private const val BuiltinMulticastOffset = 0
    private const val BuiltinUnicastOffset = 10
    private const val UserMulticastOffset = 1
    private const val UserUnicastOffset = 11

    fun candidates(
        domainId: Int,
        participantId: Int = 0,
    ): List<DdsRtpsPortCandidate> {
        require(domainId >= 0) { "domainId must be greater than or equal to 0." }
        require(participantId >= 0) { "participantId must be greater than or equal to 0." }

        val domainBase = PortBase + DomainIdGain * domainId
        val participantOffset = ParticipantIdGain * participantId

        return listOf(
            DdsRtpsPortCandidate(
                name = "builtin_multicast",
                port = domainBase + BuiltinMulticastOffset,
                description = "RTPS builtin multicast traffic candidate.",
            ),
            DdsRtpsPortCandidate(
                name = "builtin_unicast",
                port = domainBase + participantOffset + BuiltinUnicastOffset,
                description = "RTPS builtin unicast traffic candidate.",
            ),
            DdsRtpsPortCandidate(
                name = "user_multicast",
                port = domainBase + UserMulticastOffset,
                description = "RTPS user multicast traffic candidate.",
            ),
            DdsRtpsPortCandidate(
                name = "user_unicast",
                port = domainBase + participantOffset + UserUnicastOffset,
                description = "RTPS user unicast traffic candidate.",
            ),
        )
    }
}
