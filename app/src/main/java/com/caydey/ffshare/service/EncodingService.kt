package com.caydey.ffshare.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.caydey.ffshare.utils.*
import com.caydey.ffshare.utils.logs.Log
import com.caydey.ffshare.utils.logs.LogsDbHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class EncodingService : Service() {

    private val binder = LocalBinder()
    private lateinit var notificationManager: EncodingNotificationManager
    private lateinit var utils: Utils
    private lateinit var settings: Settings
    private lateinit var ffmpegParamMaker: FFmpegParamMaker
    private lateinit var logsDbHelper: LogsDbHelper

    private val _encodingState = MutableStateFlow<EncodingState>(EncodingState.Idle)
    val encodingState: StateFlow<EncodingState> = _encodingState.asStateFlow()

    private var fileStartTime: Long = 0
    private var isEncoding = false

    inner class LocalBinder : Binder() {
        fun getService(): EncodingService = this@EncodingService
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == EncodingNotificationManager.ACTION_STOP) {
                cancelEncoding()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = EncodingNotificationManager(this)
        utils = Utils(this)
        settings = Settings(this)
        ffmpegParamMaker = FFmpegParamMaker(settings, utils)
        logsDbHelper = LogsDbHelper(this)

        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(EncodingNotificationManager.ACTION_STOP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: Must call startForeground() immediately on Android O+ to avoid
        // ForegroundServiceDidNotStartInTimeException. On Android 14+ (API 34),
        // this must happen within ~10 seconds of startForegroundService() call.
        val notification = notificationManager.createInitialNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                EncodingNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(EncodingNotificationManager.NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    fun startEncoding(inputUris: ArrayList<Uri>) {
        if (isEncoding) {
            Timber.w("Encoding already in progress")
            return
        }

        isEncoding = true
        // Note: startForeground() is already called in onStartCommand() to comply
        // with Android 14+ foreground service requirements

        processFiles(inputUris)
    }

    private fun processFiles(inputUris: ArrayList<Uri>) {
        val compressedFiles = ArrayList<Uri>()
        var totalInputSize = 0L
        var totalOutputSize = 0L
        val totalFiles = inputUris.size

        fun processNext(index: Int, @Suppress("UNUSED_PARAMETER") hadError: Boolean) {
            if (index >= totalFiles) {
                // All files processed
                _encodingState.value = EncodingState.Completed(
                    compressedFiles, totalInputSize, totalOutputSize
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isEncoding = false
                return
            }

            val inputUri = inputUris[index]
            compressSingleFile(inputUri, index + 1, totalFiles,
                onSuccess = { uri, inputSize, outputSize ->
                    compressedFiles.add(uri)
                    totalInputSize += inputSize
                    totalOutputSize += outputSize
                    processNext(index + 1, false)
                },
                onFailure = {
                    // Continue with next file even if one fails
                    processNext(index + 1, true)
                }
            )
        }

        processNext(0, false)
    }

    private fun compressSingleFile(
        inputUri: Uri,
        currentFile: Int,
        totalFiles: Int,
        onSuccess: (Uri, Long, Long) -> Unit,
        onFailure: () -> Unit
    ) {
        val inputFileName = utils.getFilenameFromUri(inputUri) ?: "unknown"
        val mediaType = utils.getMediaType(inputUri)

        if (!utils.isSupportedMediaType(mediaType)) {
            val errorMsg = "Unsupported media type: $mediaType"
            Timber.e("$errorMsg for $inputFileName")
            logsDbHelper.addLog(
                Log("N/A", inputFileName, "N/A", false, errorMsg, 0, -1)
            )
            onFailure()
            return
        }

        val (outputFile, outputMediaType) = utils.getCacheOutputFile(inputUri, mediaType)
        val outputUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            outputFile
        )

        val mediaInfo = FFprobeKit.getMediaInformation(
            FFmpegKitConfig.getSafParameterForRead(this, inputUri)
        ).mediaInformation

        if (mediaInfo == null) {
            val errorMsg = "Unable to get media information"
            Timber.e("$errorMsg for $inputFileName")
            logsDbHelper.addLog(
                Log("N/A", inputFileName, "N/A", false, errorMsg, 0, -1)
            )
            onFailure()
            return
        }

        val inputFileSize = mediaInfo.size?.toLong() ?: 0L
        val showProgress = !utils.isImage(mediaType)
        val duration = if (showProgress) {
            (mediaInfo.duration?.toFloatOrNull()?.times(1000))?.toInt() ?: 0
        } else 0

        val ffmpegParams = ffmpegParamMaker.create(inputUri, mediaInfo, mediaType, outputMediaType)
        val inputSaf = FFmpegKitConfig.getSafParameterForRead(this, inputUri)
        val outputSaf = FFmpegKitConfig.getSafParameterForWrite(this, outputUri)
        val command = "-y -i $inputSaf $ffmpegParams $outputSaf"
        val prettyCommand = "ffmpeg -y -i $inputFileName $ffmpegParams ${outputFile.name}"

        Timber.i("Encoding $inputFileName")
        Timber.d("Executing: $prettyCommand")
        fileStartTime = System.currentTimeMillis()

        // Update initial state
        _encodingState.value = EncodingState.Encoding(
            currentFile = currentFile,
            totalFiles = totalFiles,
            fileName = inputFileName,
            progressPercent = 0f,
            processedTime = 0,
            totalDuration = duration,
            estimatedTimeRemaining = 0,
            outputSize = 0
        )
        notificationManager.notify(notificationManager.updateProgress(_encodingState.value as EncodingState.Encoding))

        FFmpegKit.executeAsync(command,
            { session ->
                if (session.returnCode.isValueSuccess) {
                    handleEncodingSuccess(
                        inputUri, inputFileName, mediaType, outputFile, outputUri,
                        outputMediaType, prettyCommand, inputFileSize, session.output, onSuccess
                    )
                } else if (session.returnCode.isValueCancel) {
                    _encodingState.value = EncodingState.Cancelled
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    isEncoding = false
                } else {
                    val errorOutput = session.output ?: "Unknown error"
                    handleEncodingFailure(
                        prettyCommand, inputFileName, outputFile.name, inputFileSize,
                        errorOutput, onFailure
                    )
                }
            },
            { /* logs callback */ },
            { stats ->
                // Progress callback
                if (!showProgress) return@executeAsync

                val progress = if (duration > 0) {
                    (stats.time.toFloat() / duration) * 100
                } else 0f

                val elapsed = System.currentTimeMillis() - fileStartTime
                val eta = if (progress > 1) {
                    ((elapsed / progress) * (100 - progress)).toLong()
                } else 0L

                val state = EncodingState.Encoding(
                    currentFile = currentFile,
                    totalFiles = totalFiles,
                    fileName = inputFileName,
                    progressPercent = progress.coerceIn(0f, 100f),
                    processedTime = stats.time.toInt(),
                    totalDuration = duration,
                    estimatedTimeRemaining = eta,
                    outputSize = stats.size
                )

                _encodingState.value = state
                notificationManager.notify(notificationManager.updateProgress(state))
            }
        )
    }

    private fun handleEncodingSuccess(
        inputUri: Uri,
        inputFileName: String,
        mediaType: Utils.MediaType,
        outputFile: java.io.File,
        outputUri: Uri,
        outputMediaType: Utils.MediaType,
        prettyCommand: String,
        inputFileSize: Long,
        sessionOutput: String,
        onSuccess: (Uri, Long, Long) -> Unit
    ) {
        // Handle EXIF
        if (settings.copyExifTags && ExifTools.isValidType(mediaType)) {
            contentResolver.openInputStream(inputUri)?.let { stream ->
                ExifTools.copyExif(stream, outputFile)
            }
        }

        val outputSize = outputFile.length()
        Timber.i("Encoding succeeded for $inputFileName: ${inputFileSize/1024}KB -> ${outputSize/1024}KB")

        // Copy to Downloads/ffshare folder
        val downloadUri = utils.copyToDownloads(outputFile, outputMediaType)
        if (downloadUri != null) {
            Timber.d("File saved to Downloads/ffshare: ${outputFile.name}")
        }

        if (settings.saveLogs) {
            logsDbHelper.addLog(
                Log(prettyCommand, inputFileName, outputFile.name, true, sessionOutput, inputFileSize, outputSize)
            )
        }
        onSuccess(outputUri, inputFileSize, outputSize)
    }

    private fun handleEncodingFailure(
        prettyCommand: String,
        inputFileName: String,
        outputFileName: String,
        inputFileSize: Long,
        errorOutput: String,
        onFailure: () -> Unit
    ) {
        Timber.e("FFmpeg encoding failed for $inputFileName")
        Timber.e("Error output: ${errorOutput.take(500)}")

        logsDbHelper.addLog(
            Log(prettyCommand, inputFileName, outputFileName, false, errorOutput, inputFileSize, -1)
        )

        _encodingState.value = EncodingState.Failed(error = "encoding failed")

        onFailure()
    }

    fun cancelEncoding() {
        Timber.d("Canceling encoding")
        FFmpegKit.cancel()
        _encodingState.value = EncodingState.Cancelled
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isEncoding = false
    }
}
