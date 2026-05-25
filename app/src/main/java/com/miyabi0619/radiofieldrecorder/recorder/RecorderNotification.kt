package com.miyabi0619.radiofieldrecorder.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.miyabi0619.radiofieldrecorder.MainActivity
import com.miyabi0619.radiofieldrecorder.R

object RecorderNotification {
    const val ChannelId = "recording"
    const val NotificationId = 10_001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "記録",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "実行中のフィールド記録セッションを表示します。"
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        sessionName: String,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            RecorderService.stopIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("記録中: $sessionName")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "停止",
                stopIntent,
            )
            .build()
    }
}
