package com.miyabi0619.radiofieldrecorder.recorder

interface RecorderClock {
    fun nowMillis(): Long
    fun elapsedRealtimeNanos(): Long
}

object SystemRecorderClock : RecorderClock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeNanos(): Long = android.os.SystemClock.elapsedRealtimeNanos()
}
