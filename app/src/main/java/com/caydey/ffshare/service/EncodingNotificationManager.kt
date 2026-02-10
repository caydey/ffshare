package com.caydey.ffshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.caydey.ffshare.HandleMediaActivity
import com.caydey.ffshare.R
import com.caydey.ffshare.utils.Utils

class EncodingNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "ffshare_encoding_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.caydey.ffshare.STOP_ENCODING"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val utils = Utils(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_encoding),
                NotificationManager.IMPORTANCE_LOW  // No sound
            ).apply {
                description = context.getString(R.string.notification_channel_encoding_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createInitialNotification(): Notification {
        return buildNotification(
            title = context.getString(R.string.notification_encoding_starting),
            progress = 0,
            indeterminate = true
        )
    }

    fun updateProgress(state: EncodingState.Encoding): Notification {
        val title = if (state.totalFiles > 1) {
            context.getString(
                R.string.notification_encoding_multiple,
                state.currentFile, state.totalFiles
            )
        } else {
            context.getString(R.string.notification_encoding)
        }

        val remainingText = if (state.estimatedTimeRemaining > 0) {
            utils.millisToMicrowaveTime(state.estimatedTimeRemaining.toInt()) +
                    " " + context.getString(R.string.remaining)
        } else {
            ""
        }

        val contentText = "${state.progressPercent.toInt()}%" +
                if (remainingText.isNotEmpty()) " - $remainingText" else ""

        return buildNotification(
            title = title,
            text = contentText,
            progress = state.progressPercent.toInt(),
            indeterminate = false
        )
    }

    private fun buildNotification(
        title: String,
        text: String = "",
        progress: Int = 0,
        indeterminate: Boolean = false
    ): Notification {
        // Intent to open activity when notification is tapped
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, HandleMediaActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop action
        val stopIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_STOP).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_stop,
                context.getString(R.string.cancel_ffmpeg),
                stopIntent
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun notify(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
