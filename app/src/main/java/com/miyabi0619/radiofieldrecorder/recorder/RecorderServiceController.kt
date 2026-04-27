package com.miyabi0619.radiofieldrecorder.recorder

import android.content.Context
import androidx.core.content.ContextCompat

object RecorderServiceController {
    fun start(
        context: Context,
        sessionId: Long,
        sessionName: String,
    ) {
        ContextCompat.startForegroundService(
            context,
            RecorderService.startIntent(
                context = context,
                sessionId = sessionId,
                sessionName = sessionName,
            ),
        )
    }

    fun stop(context: Context) {
        context.startService(RecorderService.stopIntent(context))
    }
}
