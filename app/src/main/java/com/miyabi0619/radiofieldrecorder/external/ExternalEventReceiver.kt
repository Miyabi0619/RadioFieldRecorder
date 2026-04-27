package com.miyabi0619.radiofieldrecorder.external

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.miyabi0619.radiofieldrecorder.data.local.RadioFieldRecorderDatabase
import com.miyabi0619.radiofieldrecorder.data.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExternalEventReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ExternalEventContract.Action) return

        val payload = ExternalEventContract.parse(
            source = intent.getStringExtra(ExternalEventContract.ExtraSource),
            type = intent.getStringExtra(ExternalEventContract.ExtraType),
            label = intent.getStringExtra(ExternalEventContract.ExtraLabel),
            value = intent.getStringExtra(ExternalEventContract.ExtraValue),
            memo = intent.getStringExtra(ExternalEventContract.ExtraMemo),
            timestamp = if (intent.hasExtra(ExternalEventContract.ExtraTimestamp)) {
                intent.getLongExtra(ExternalEventContract.ExtraTimestamp, 0L)
            } else {
                null
            },
        ) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RecordingRepository(
                    RadioFieldRecorderDatabase.create(context.applicationContext),
                )
                val session = repository.getLatestRunningSession()
                if (session == null) {
                    Log.i(Tag, "External event ignored because no session is running.")
                    return@launch
                }

                repository.addEvent(
                    sessionId = session.id,
                    timestamp = payload.timestamp ?: System.currentTimeMillis(),
                    type = payload.eventType(),
                    label = payload.label,
                    memo = payload.eventMemo(),
                )
            } catch (error: Throwable) {
                Log.w(Tag, "Failed to store external event.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val Tag = "ExternalEventReceiver"
    }
}
