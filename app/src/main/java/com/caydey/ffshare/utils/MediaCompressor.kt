package com.caydey.ffshare.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.*
import com.caydey.ffshare.R
import timber.log.Timber


import java.util.*


class MediaCompressor(private val context: Context) {
    private val utils: Utils by lazy { Utils(context) }
    private val settings: Settings by lazy { Settings(context) }


    fun cancelAllOperations() {
        Timber.d("Canceling all ffmpeg operations")
        FFmpegKit.cancel()
    }

    @SuppressLint("SetTextI18n")
    fun compressSingleFile(
        activity: Activity,
        inputFileUri: Uri,
        successHandler: (uri: Uri, inputFileSize: Long, outputFileSize: Long) -> Unit,
        failureHandler: () -> Unit
    ) {
        val txtFfmpegCommand: TextView = activity.findViewById(R.id.txtFfmpegCommand)
        val txtInputFile: TextView = activity.findViewById(R.id.txtInputFile)
        val txtInputFileSize: TextView = activity.findViewById(R.id.txtInputFileSize)
        val txtOutputFile: TextView = activity.findViewById(R.id.txtOutputFile)
        val txtOutputFileSize: TextView = activity.findViewById(R.id.txtOutputFileSize)
        val txtProcessedTime: TextView = activity.findViewById(R.id.txtProcessedTime)
        val txtProcessedTimeTotal: TextView = activity.findViewById(R.id.txtProcessedTimeTotal)
        val txtProcessedPercent: TextView = activity.findViewById(R.id.txtProcessedPercent)
        val processedTableRow: TableRow = activity.findViewById(R.id.processedTableRow)

        // cancel button
        val btnCancel: Button = activity.findViewById(R.id.btnCancel)
        btnCancel.setOnClickListener {
            Toast.makeText(context, context.getString(R.string.canceled), Toast.LENGTH_LONG).show()
            // cancel all ffmpeg operations
            cancelAllOperations()

            failureHandler() // a cancel is a fail
        }

        val mediaType = utils.getMediaType(inputFileUri)
        if (mediaType == Utils.MediaType.UNKNOWN) {
            Toast.makeText(context, context.getString(R.string.error_unknown_filetype), Toast.LENGTH_LONG).show()
            failureHandler()
            return
        }

        val showProgress = !utils.isImage(mediaType)
        if (!showProgress) {
            processedTableRow.visibility = View.INVISIBLE
        }

        val inputFileName = utils.getFilenameFromUri(inputFileUri)

        // get output file, (random uuid, custom name, original name)
        val (outputFile, outputFileMediaType) = utils.getCacheOutputFile(inputFileUri, mediaType)

        // get Uri from File, needs to be this way not Uri.fromFile(...) to go through security
        val outputFileUri = FileProvider.getUriForFile(context, context.applicationContext.packageName+".fileprovider", outputFile)


        // need to create new saf param as they are one-use
        val mediaInformation = FFprobeKit.getMediaInformation(FFmpegKitConfig.getSafParameterForRead(context, inputFileUri)).mediaInformation
        val inputFileSize = mediaInformation.size.toLong() // get input file size

        var duration = 0 // default duration for image
        if (showProgress) {
            // invalid video file if ffprobe cant parse duration and size
            if (mediaInformation.duration == null || mediaInformation.size == null) {
                Timber.d("Unable to get size & duration for media, throwing error")
                Toast.makeText(context, context.getString(R.string.error_invalid_file), Toast.LENGTH_LONG).show()
                failureHandler()
                return
            }
            duration = (mediaInformation.duration.toFloat() * 1_000).toInt()
        }

        val params = createFFmpegParams(inputFileUri, mediaType, outputFileMediaType)
        val inputSaf: String = FFmpegKitConfig.getSafParameterForRead(context, inputFileUri)
        val outputSaf: String = FFmpegKitConfig.getSafParameterForWrite(context, outputFileUri)
        val command = "-y -i $inputSaf $params $outputSaf"

        // set TextViews
        Handler(Looper.getMainLooper()).post {
            txtFfmpegCommand.text = "ffmpeg -y -i $inputFileName $params ${outputFile.name}"
            txtInputFile.text = inputFileName
            txtInputFileSize.text = utils.bytesToHuman(inputFileSize)
            txtOutputFile.text = outputFile.name
            txtOutputFileSize.text = utils.bytesToHuman(0)
            txtProcessedTime.text = utils.millisToMicrowave(0)
            txtProcessedTimeTotal.text = utils.millisToMicrowave(duration)
            txtProcessedPercent.text = context.getString(R.string.format_percentage, 0.0f)
        }

        Timber.d("Executing ffmpeg command: 'ffmpeg %s'", command)
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_QUIET) // hide built in ffmpeg logs
        FFmpegKit.executeAsync(command, { session ->
            // completed
            if (!session.returnCode.isValueSuccess) { // failed
                val toastMessage = if (session.returnCode.isValueCancel) {
                    Timber.d("ffmpeg command canceled")
                    context.getString(R.string.ffmpeg_canceled)
                } else {
                    Timber.d("ffmpeg command failed")
                    context.getString(R.string.ffmpeg_error)
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
                }
                failureHandler()
            } else { // success
                Timber.d("ffmpeg command executed successfully")
                if (settings.copyExifTags && ExifTools.isValidType(mediaType)) {
                    Timber.d("copying exif tags")
                    ExifTools.copyExif(context.contentResolver.openInputStream(inputFileUri)!!, outputFile)
                }
                // """Only the original thread that created a view hierarchy can touch its views."""
                Handler(Looper.getMainLooper()).post {
                    // update TextViews to their final values 97.8% -> 100.0%
                    txtProcessedPercent.text = context.getString(R.string.format_percentage, 100.0f)
                    txtProcessedTime.text = utils.millisToMicrowave(duration)
                    txtOutputFileSize.text = utils.bytesToHuman(outputFile.length())
                }
                // callback
                successHandler(outputFileUri, inputFileSize, outputFile.length())
            }
        }, { /* logs */ }, { statistics ->
            // update TextViews with stats
            Handler(Looper.getMainLooper()).post {
                if (showProgress) { // only show time processed if video
                    txtProcessedPercent.text = context.getString(R.string.format_percentage, (statistics.time.toFloat() / duration) * 100)
                    txtProcessedTime.text = utils.millisToMicrowave(statistics.time)
                }
                txtOutputFileSize.text = utils.bytesToHuman(statistics.size)
            }
        })
    }

    fun compressFiles(activity: Activity, inputFilesUri: ArrayList<Uri>, callback: (uris: ArrayList<Uri>?) -> Unit) {
        val txtCommandNumber: TextView = activity.findViewById(R.id.txtCommandNumber)

        val inputFilesCount = inputFilesUri.size

        val compressedFiles = ArrayList<Uri>()

        var totalInputFileSize = 0L
        var totalOutputFileSize = 0L

        // since we are working with callbacks a simple for loop wont work
        lateinit var iteratorFunction: (Int) -> Unit
        iteratorFunction = fun(i) {
            if (inputFilesCount > 1) { // show "1 of N" label if N > 1
                Handler(Looper.getMainLooper()).post {
                    txtCommandNumber.text = context.getString(R.string.x_of_y, i+1, inputFilesCount)
                }
            }
            Timber.d("Processing %d of %d files", i+1, inputFilesCount)

            compressSingleFile(activity, inputFilesUri[i], failureHandler = {
                // if 1 file fails they all do
                callback(null)
            }, successHandler = { uri, inputFileSize, outputFileSize ->
                totalInputFileSize += inputFileSize
                totalOutputFileSize += outputFileSize
                compressedFiles.add(uri)
                if (i+1 < inputFilesCount) { // base case
                    iteratorFunction(i+1) // call to self
                } else { // end of iterations
                    // show compression percentage as toast message
                    if (settings.showStatusMessages) {
                        val totalOutputFileSizeHuman = utils.bytesToHuman(totalOutputFileSize)
                        val compressionPercentage = (1 - (totalOutputFileSize.toDouble() / totalInputFileSize)) * 100
                        val toastMessage = context.getString(R.string.media_reduction_message, totalOutputFileSizeHuman, compressionPercentage)
                        Handler(Looper.getMainLooper()).post {
                            Timber.d("Showing compression size toast message")
                            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                    callback(compressedFiles)
                }
            })
        }
        iteratorFunction(0) // start iterations
    }

    private fun createFFmpegParams(inputFile: Uri, mediaType: Utils.MediaType, outputFileMediaType: Utils.MediaType): String {
        val params = StringJoiner(" ")

        // video
        if (utils.isVideo(mediaType)) {
            // crf
            params.add("-crf ${settings.videoCrf}")
            // max bitrate
            if (outputFileMediaType != Utils.MediaType.WEBM) { // ffmpeg does not like these params when the output file is webm
                params.add("-maxrate ${settings.videoMaxBitrate} -bufsize ${settings.videoMaxBitrate}")
            }
            params.add("-maxrate ${settings.videoMaxBitrate} -bufsize ${settings.videoMaxBitrate}")
            // pixel format
            params.add("-vf format=yuv420p")
        }

        // jpeg quality
        if (mediaType == Utils.MediaType.JPEG) {
            val qscale = settings.jpegQscale
            params.add("-qscale:v $qscale")
        }

        // correct images rotation, this version of ffmpeg wipes orientation metadata
        if (utils.isImage(mediaType)) {
            // don't correct image rotation if exif tags are kept (orientation tag wont be destroyed)
            if (!(settings.copyExifTags && ExifTools.isValidType(mediaType))) {
                val orientationParam = when(utils.getImageOrientation(inputFile)) {
                    Utils.Orientation.ROT_0 -> ""
                    Utils.Orientation.ROT_90 -> "-vf \"transpose=1\"" // clockwise 90
                    Utils.Orientation.ROT_180 -> "-vf \"transpose=2,transpose=2\"" // counter clockwise 90 x 2
                    Utils.Orientation.ROT_270 -> "-vf \"transpose=2\"" // counter clockwise 90
                }
                params.add(orientationParam)
            }
        }
        return params.toString()
    }
}
