package com.caydey.ffshare.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.caydey.ffshare.R
import com.caydey.ffshare.utils.MediaCompressor
import com.caydey.ffshare.utils.Utils
import timber.log.Timber

const val MEDIA_NOTIFICATION_ID = 2600
const val MEDIA_NOTIFICATION_CHANNEL = "MEDIA_SERVICE_ONGOING_NOTIFICATION_CHANNEL"

class HandleMediaService : Service() {
    private val mediaCompressor: MediaCompressor by lazy { MediaCompressor(applicationContext) }
    private val utils: Utils by lazy { Utils(applicationContext) }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, MEDIA_NOTIFICATION_CHANNEL)
    }
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    private val mediaServiceBinder = HandleMediaBinder()

    inner class HandleMediaBinder : Binder() {
        fun getService(): HandleMediaService = this@HandleMediaService
    }

    private var progressHandlerCallback: ProgressCallback? = null

    interface ProgressCallback {
        fun onProgressUpdate(progress: Float)
    }

    enum class ServiceActions {
        START, STOP,
    }

    override fun onBind(p0: Intent?): IBinder {
        return mediaServiceBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ServiceActions.START.toString() -> {
                startSelf(intent)
            }

            ServiceActions.STOP.toString() -> {
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    private fun startSelf(intent: Intent?) {
        createNotification()

        val receivedMedia: ArrayList<Uri> =
            intent?.getParcelableArrayListExtra(Intent.EXTRA_STREAM)!!

        if (receivedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_uri_intent), Toast.LENGTH_LONG).show()
            Timber.d("No files found in shared intent")
            stopSelf()
        } else {
            mediaCompressor.bareCompressFiles(
                receivedMedia,
                progressHandler = { index, total, progress ->

                    progressHandlerCallback?.onProgressUpdate(progress)

                    updateNotificationContent(
                        getString(
                            R.string.media_service_notification_progress, index + 1, total, progress
                        )
                    )
//                    Timber.d("startSelf index: $index")
//                    Timber.d("startSelf total: $total")
//                    Timber.d("startSelf progress: $progress")
                },
                successHandler = { compressedMedia ->
                    updateNotificationContent(
                        getString(
                            R.string.media_service_notification_done,
                            compressedMedia.size,
                            compressedMedia.size
                        )
                    )

                    if (compressedMedia.isNotEmpty()) {
                        shareMedia(compressedMedia)
                    }
                }
            )
        }
    }

    private fun createNotification() {
        val openActivityIntent = Intent(this, HandleMediaService::class.java)
            .let { notificationIntent ->
                PendingIntent.getActivity(
                    this,
                    2700,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val stopServiceIntent = PendingIntent.getService(
            this,
            2800,
            Intent(this, HandleMediaService::class.java)
                .apply { action = ServiceActions.STOP.toString() },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = notificationBuilder
            .setContentTitle(getText(R.string.media_service_notification_title))
            .setContentText(getText(R.string.media_service_notification_message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openActivityIntent)
            .setOngoing(true) // persistent notification
            .setOnlyAlertOnce(true) // prevent making sound
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.cancel_ffmpeg),
                stopServiceIntent
            )
            .build()

        startForeground(MEDIA_NOTIFICATION_ID, notification)
    }

    private fun updateNotificationContent(content: String) {
        notificationBuilder.setContentText(content)
        notificationManager.notify(MEDIA_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun shareMedia(mediaUris: ArrayList<Uri>) {
        val shareIntent = Intent()

        // temp permissions for other app to view file
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // add compressed media files
        if (mediaUris.size == 1) {
            Timber.d("Creating share intent for single item")
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUris[0])
        } else {
            Timber.d("Creating share intent for multiple items")
            shareIntent.action = Intent.ACTION_SEND_MULTIPLE
            shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUris)
        }

        // set mime for each file
        mediaUris.forEach { mediaUri ->
            shareIntent.setDataAndType(mediaUri, contentResolver.getType(mediaUri))
        }

        val chooserIntent = PendingIntent.getActivity(
            this,
            2900,
            Intent.createChooser(shareIntent, "media"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder.setContentIntent(chooserIntent)
        notificationManager.notify(MEDIA_NOTIFICATION_ID, notificationBuilder.build())

        // startActivity(chooserIntent)
    }

    fun setProgressHandlerCallback(callback: ProgressCallback?) {
        progressHandlerCallback = callback
    }
}