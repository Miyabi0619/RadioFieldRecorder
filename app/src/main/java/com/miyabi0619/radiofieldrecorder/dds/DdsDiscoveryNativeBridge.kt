package com.miyabi0619.radiofieldrecorder.dds

class DdsDiscoveryNativeBridge {
    init {
        System.loadLibrary("dds_discovery_monitor")
    }

    fun start(domainId: Int): Boolean =
        nativeStart(domainId)

    fun stop() {
        nativeStop()
    }

    fun snapshotJson(): String =
        nativeSnapshotJson()

    fun snapshot(): DdsDiscoverySnapshot =
        DdsDiscoverySnapshotParser.parse(snapshotJson())

    private external fun nativeStart(domainId: Int): Boolean
    private external fun nativeStop()
    private external fun nativeSnapshotJson(): String
}
