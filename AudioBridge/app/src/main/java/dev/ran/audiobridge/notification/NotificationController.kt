package dev.ran.audiobridge.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.ran.audiobridge.MainActivity
import dev.ran.audiobridge.R

class NotificationController(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "audio_bridge_playback"
        const val NOTIFICATION_ID = 1001
    }

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AudioBridge Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "AudioBridge 后台播放状态"
        }
        manager.createNotificationChannel(channel)
    }

    fun build(statusMessage: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AudioBridge")
            .setContentText(statusMessage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
