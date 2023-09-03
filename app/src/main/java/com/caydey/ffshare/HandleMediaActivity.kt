package com.caydey.ffshare

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.caydey.ffshare.services.HandleMediaService
import com.caydey.ffshare.services.HandleMediaService.ProgressCallback
import com.caydey.ffshare.utils.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
import com.caydey.ffshare.utils.MediaCompressor
import com.caydey.ffshare.utils.Utils
import timber.log.Timber


class HandleMediaActivity : AppCompatActivity(), ProgressCallback {
    // by lazy means load when variable is used, lazy-loading helps performance
    // also without it there is a null error for applicationContext
    private val mediaCompressor: MediaCompressor by lazy { MediaCompressor(applicationContext) }
    private val utils: Utils by lazy { Utils(applicationContext) }

    private lateinit var handleMediaService: HandleMediaService
    private var isBoundToMediaService = false


    private val mediaServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {

            val binder = service as HandleMediaService.HandleMediaBinder
            handleMediaService = binder.getService()
            handleMediaService.setProgressHandlerCallback(this@HandleMediaActivity)
            isBoundToMediaService = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBoundToMediaService = false
        }
    }

    override fun onProgressUpdate(progress: Float) {
        // TODO: Retrieve all other information and display them
        Handler(Looper.getMainLooper()).post {
            val txtProcessedPercent: TextView = findViewById(R.id.txtProcessedPercent)
            txtProcessedPercent.text = getString(R.string.format_percentage, progress)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handle_media)

        if (utils.isReadPermissionGranted) {
            onMediaReceive()
        } else {
            Timber.d("Requesting read permissions")
            utils.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    // TODO: Add this when the app is upgraded to support Android 13
                    // Manifest.permission.POST_NOTIFICATIONS,
                )
            )
        }
    }

    override fun finish() {
        scheduleCacheCleanup()
        super.finish()
    }


    override fun onDestroy() {
        mediaCompressor.cancelAllOperations()
        if (isBoundToMediaService) {
            handleMediaService.setProgressHandlerCallback(null)
            unbindService(mediaServiceConnection)
            isBoundToMediaService = false
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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

        // Start foreground service to start conversion
        if (receivedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_uri_intent), Toast.LENGTH_LONG).show()
            Timber.d("No files found in shared intent")
            finish()
        } else {
            Intent(this, HandleMediaService::class.java)
                .apply {
                    action = HandleMediaService.ServiceActions.START.toString()
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, receivedMedia)
                }
                .also {
                    startService(it)
                    bindService(it, mediaServiceConnection, Context.BIND_AUTO_CREATE)
                }
        }
    }

    private fun scheduleCacheCleanup() {
        Timber.d("Scheduling cleanup alarm")
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, CacheCleanUpReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )

        // every 12 hours clear cache
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime(),
            AlarmManager.INTERVAL_HALF_DAY,
            pendingIntent
        )
    }
}
