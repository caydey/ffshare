package com.caydey.ffshare

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.caydey.ffshare.utils.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
import com.caydey.ffshare.utils.MediaCompressor
import com.caydey.ffshare.utils.Utils
import timber.log.Timber


class HandleMediaActivity : AppCompatActivity() {
    // by lazy means load when variable is used, lazy-loading helps performance
    // also without it there is a null error for applicationContext
    private val mediaCompressor: MediaCompressor by lazy { MediaCompressor(applicationContext) }
    private val utils: Utils by lazy { Utils(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handle_media)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (utils.isReadPermissionGranted) {
            onMediaReceive()
        } else {
            Timber.d("Requesting read permissions")
            utils.requestReadPermissions(this)
        }
    }

    override fun finish() {
        mediaCompressor.cancelAllOperations()
        scheduleCacheCleanup()
        super.finish()
    }

    override fun onStop() {
        mediaCompressor.cancelAllOperations()
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // first time running app user is requested to allow app to read external storage,
        // after clicking "allow" the app will continue handling media it was shared
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            Timber.d("Read permissions granted, continuing...")
            onMediaReceive()
        }
    }

    private fun onMediaReceive() {
        // "intent" variable is the shared item
        val receivedMedia = when (intent.action) {
            Intent.ACTION_SEND -> arrayListOf(intent.getParcelableExtra(Intent.EXTRA_STREAM)!!)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)!!
            else -> ArrayList<Uri>()
        }
        // unable to get file from intent
        if (receivedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_uri_intent), Toast.LENGTH_LONG).show()
            Timber.d("No files found in shared intent")
            finish()
        } else {
            // callback
            mediaCompressor.compressFiles(this, receivedMedia) { compressedMedia ->
                if (compressedMedia.isNotEmpty()) {
                    shareMedia(compressedMedia)
                }
                finish()
            }
        }
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

        val chooser = Intent.createChooser(shareIntent, "media")
        startActivity(chooser)
    }

    private fun scheduleCacheCleanup() {
        Timber.d("Scheduling cleanup alarm")
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, CacheCleanUpReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

        // every 12 hours clear cache
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime(),
            AlarmManager.INTERVAL_HALF_DAY,
            pendingIntent
        )
    }
}
