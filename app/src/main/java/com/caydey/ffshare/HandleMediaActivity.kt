package com.caydey.ffshare

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.caydey.ffshare.extensions.parcelable
import com.caydey.ffshare.extensions.parcelableArrayList
import com.caydey.ffshare.service.EncodingService
import com.caydey.ffshare.service.EncodingState
import com.caydey.ffshare.utils.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
import com.caydey.ffshare.utils.Settings
import com.caydey.ffshare.utils.Utils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber


class HandleMediaActivity : AppCompatActivity() {
    private val utils: Utils by lazy { Utils(applicationContext) }
    private val settings: Settings by lazy { Settings(applicationContext) }

    private var encodingService: EncodingService? = null
    private var bound = false
    private var pendingMediaUris: ArrayList<Uri>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as EncodingService.LocalBinder
            encodingService = binder.getService()
            bound = true
            observeEncodingState()

            // Start encoding if we have pending URIs
            pendingMediaUris?.let { uris ->
                encodingService?.startEncoding(uris)
                pendingMediaUris = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            encodingService = null
            bound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* We proceed regardless of result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handle_media)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupCancelButton()
        requestNotificationPermissionIfNeeded()

        if (utils.isReadPermissionGranted) {
            onMediaReceive()
        } else {
            Timber.d("Requesting read permissions")
            utils.requestReadPermissions(this)
        }
    }

    private fun setupCancelButton() {
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            encodingService?.cancelEncoding()
            Toast.makeText(this, getString(R.string.ffmpeg_canceled), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun finish() {
        // Don't cancel encoding on finish - let it continue in background
        if (bound) {
            unbindService(connection)
            bound = false
        }
        scheduleCacheCleanup()
        super.finish()
    }

    // Note: We no longer cancel on onStop() - the service continues in background!

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            Timber.d("Read permissions granted, continuing...")
            onMediaReceive()
        }
    }

    private fun onMediaReceive() {
        val receivedMedia: ArrayList<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> arrayListOf(intent.parcelable<Uri>(Intent.EXTRA_STREAM)!!)
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayList<Uri>(Intent.EXTRA_STREAM)!!
            else -> ArrayList<Uri>()
        }

        if (receivedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_uri_intent), Toast.LENGTH_LONG).show()
            Timber.d("No files found in shared intent")
            finish()
            return
        }

        // Store URIs and start/bind to service
        pendingMediaUris = receivedMedia

        val serviceIntent = Intent(this, EncodingService::class.java)
        // Must use startForegroundService on Android O+ for foreground services
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun observeEncodingState() {
        lifecycleScope.launch {
            encodingService?.encodingState?.collectLatest { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: EncodingState) {
        when (state) {
            is EncodingState.Encoding -> {
                val txtInputFile: TextView = findViewById(R.id.txtInputFile)
                val txtOutputFileSize: TextView = findViewById(R.id.txtOutputFileSize)
                val txtProcessedTime: TextView = findViewById(R.id.txtProcessedTime)
                val txtProcessedTimeTotal: TextView = findViewById(R.id.txtProcessedTimeTotal)
                val txtProcessedPercent: TextView = findViewById(R.id.txtProcessedPercent)
                val txtCommandNumber: TextView = findViewById(R.id.txtCommandNumber)
                val processedTableRow: TableRow = findViewById(R.id.processedTableRow)

                txtInputFile.text = state.fileName
                txtOutputFileSize.text = utils.bytesToHuman(state.outputSize)
                txtProcessedPercent.text = getString(R.string.format_percentage, state.progressPercent)
                txtProcessedTime.text = utils.millisToMicrowaveTime(state.processedTime)
                txtProcessedTimeTotal.text = utils.millisToMicrowaveTime(state.totalDuration)

                // Show progress row only for videos (not images)
                if (state.totalDuration > 0) {
                    processedTableRow.visibility = View.VISIBLE
                } else {
                    processedTableRow.visibility = View.INVISIBLE
                }

                if (state.totalFiles > 1) {
                    txtCommandNumber.text = getString(R.string.command_x_of_y, state.currentFile, state.totalFiles)
                    txtCommandNumber.visibility = View.VISIBLE
                }
            }

            is EncodingState.Completed -> {
                if (state.outputUris.isNotEmpty()) {
                    showCompletionToast(state)
                    shareMedia(state.outputUris)
                }
                finish()
            }

            is EncodingState.Failed -> {
                Toast.makeText(this, getString(R.string.ffmpeg_error), Toast.LENGTH_LONG).show()
                finish()
            }

            is EncodingState.Cancelled -> {
                finish()
            }

            EncodingState.Idle -> {
                // Initial state, do nothing
            }
        }
    }

    private fun showCompletionToast(state: EncodingState.Completed) {
        if (settings.showStatusMessages && state.totalInputSize > 0) {
            val compressionPercent =
                (1 - (state.totalOutputSize.toDouble() / state.totalInputSize)) * 100
            val message = getString(
                R.string.media_reduction_message,
                utils.bytesToHuman(state.totalOutputSize),
                compressionPercent
            )
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun shareMedia(mediaUris: ArrayList<Uri>) {
        val shareIntent = Intent()

        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (mediaUris.size == 1) {
            Timber.d("Creating share intent for single item")
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUris[0])
        } else {
            Timber.d("Creating share intent for multiple items")
            shareIntent.action = Intent.ACTION_SEND_MULTIPLE
            shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUris)
        }

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
