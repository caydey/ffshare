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
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.caydey.ffshare.R
import com.caydey.ffshare.utils.logs.Log
import com.caydey.ffshare.utils.logs.LogsDbHelper
import timber.log.Timber
import java.util.*


class MediaCompressor(private val context: Context) {
    private val utils: Utils by lazy { Utils(context) }
    private val settings: Settings by lazy { Settings(context) }
    private val logsDbHelper by lazy { LogsDbHelper(context) }

    private val ffmpegParamMaker = FFmpegParamMaker(settings, utils)

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
            Toast.makeText(context, context.getString(R.string.ffmpeg_canceled), Toast.LENGTH_LONG).show()
            // cancel all ffmpeg operations
            cancelAllOperations()

            failureHandler() // a cancel is a fail
        }

        val mediaType = utils.getMediaType(inputFileUri)
        if (!utils.isSupportedMediaType(mediaType)) { // not supported show error
            Toast.makeText(context, context.getString(R.string.error_unknown_filetype), Toast.LENGTH_LONG).show()
            failureHandler()
            return
        }

        // don't show progress when compressing images (not possible)
        val showProgress = !utils.isImage(mediaType)
        if (!showProgress) {
            processedTableRow.visibility = View.INVISIBLE
        }

        val inputFileName = utils.getFilenameFromUri(inputFileUri) ?: "unknown"

        // get output file, (random uuid, custom name, original name)
        val (outputFile, outputMediaType) = utils.getCacheOutputFile(inputFileUri, mediaType)

        // get Uri from File, needs to be this way not Uri.fromFile(...) to go through security
        val outputFileUri = FileProvider.getUriForFile(context, context.applicationContext.packageName+".fileprovider", outputFile)

        // need to create new saf param as they are one-use
        val mediaInformation = FFprobeKit.getMediaInformation(FFmpegKitConfig.getSafParameterForRead(context, inputFileUri)).mediaInformation

        if (mediaInformation == null) {
            Timber.d("Unable to get media information, throwing error")
            Toast.makeText(context, context.getString(R.string.error_invalid_file), Toast.LENGTH_LONG).show()
            failureHandler()
            return
        }

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

        val ffmpegParams = ffmpegParamMaker.create(inputFileUri, mediaInformation, mediaType, outputMediaType)
        val inputSaf: String = FFmpegKitConfig.getSafParameterForRead(context, inputFileUri)
        val outputSaf: String = FFmpegKitConfig.getSafParameterForWrite(context, outputFileUri)
        val command = "-y -i $inputSaf $ffmpegParams $outputSaf"
        val prettyCommand = "ffmpeg -y -i $inputFileName $ffmpegParams ${outputFile.name}"

        // set TextViews
        Handler(Looper.getMainLooper()).post {
            txtFfmpegCommand.text = prettyCommand
            txtInputFile.text = inputFileName
            txtInputFileSize.text = utils.bytesToHuman(inputFileSize)
            txtOutputFile.text = outputFile.name
            txtOutputFileSize.text = utils.bytesToHuman(0)
            txtProcessedTime.text = utils.millisToMicrowaveTime(0)
            txtProcessedTimeTotal.text = utils.millisToMicrowaveTime(duration)
            txtProcessedPercent.text = context.getString(R.string.format_percentage, 0.0f)
        }

        Timber.d("Executing ffmpeg command: 'ffmpeg %s'", command)
        FFmpegKit.executeAsync(command, { session ->
            // completed
            if (!session.returnCode.isValueSuccess) { // failed
                if (!session.returnCode.isValueCancel) { // failure was not caused by a cancel
                    Timber.d("ffmpeg command failed")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, context.getString(R.string.ffmpeg_error), Toast.LENGTH_LONG).show()
                    }
                    // save log

                    logsDbHelper.addLog(Log(
                        prettyCommand,
                        inputFileName,
                        outputFile.name,
                        false,
                        session.output,
                        inputFileSize,
                        -1
                    ))
                    failureHandler()
                }
            } else { // success
                Timber.d("ffmpeg command executed successfully")
                if (settings.copyExifTags && ExifTools.isValidType(mediaType)) {
                    Timber.d("copying exif tags")
                    ExifTools.copyExif(context.contentResolver.openInputStream(inputFileUri)!!, outputFile)
                }
                val outputFileCurrentSize = outputFile.length()
                // """Only the original thread that created a view hierarchy can touch its views."""
                Handler(Looper.getMainLooper()).post {
                    // update TextViews to their final values 97.8% -> 100.0%
                    txtProcessedPercent.text = context.getString(R.string.format_percentage, 100.0f)
                    txtProcessedTime.text = utils.millisToMicrowaveTime(duration)
                    if (outputFileCurrentSize > 0) {
                        txtOutputFileSize.text = utils.bytesToHuman(outputFileCurrentSize)
                    }
                }

                logsDbHelper.addLog(Log(
                    prettyCommand,
                    inputFileName,
                    outputFile.name,
                    true,
                    session.output,
                    inputFileSize,
                    outputFileCurrentSize
                ))
                // callback
                successHandler(outputFileUri, inputFileSize, outputFileCurrentSize)
            }
        }, { /* logs */ }, { statistics ->
            // update TextViews with stats
            Handler(Looper.getMainLooper()).post {
                if (showProgress) { // only show time processed if video
                    txtProcessedPercent.text = context.getString(R.string.format_percentage, (statistics.time.toFloat() / duration) * 100)
                    txtProcessedTime.text = utils.millisToMicrowaveTime(statistics.time.toInt())
                }
                txtOutputFileSize.text = utils.bytesToHuman(statistics.size)
            }
        })
    }

    fun compressFiles(activity: Activity, inputFilesUri: ArrayList<Uri>, callback: (uris: ArrayList<Uri>) -> Unit) {
        val txtCommandNumber: TextView = activity.findViewById(R.id.txtCommandNumber)

        val inputFilesCount = inputFilesUri.size

        val compressedFiles = ArrayList<Uri>()

        var totalInputFileSize = 0L
        var totalOutputFileSize = 0L

        // since we are working with callbacks a simple for loop wont work
        lateinit var iteratorFunction: (Int, Boolean) -> Unit
        iteratorFunction = fun(i, error) {
            // base case
            if (i < inputFilesCount) {
                Timber.d("Processing %d of %d files", i+1, inputFilesCount)

                if (inputFilesCount > 1) { // show "1 of N" label if N > 1
                    Handler(Looper.getMainLooper()).post {
                        txtCommandNumber.text = context.getString(R.string.command_x_of_y, i+1, inputFilesCount)
                    }
                }

                compressSingleFile(activity, inputFilesUri[i], failureHandler = {
                    // if 1 file fails don't add it to compressedFiles array
                    iteratorFunction(i+1, true) // call to self with error flag true
                }, successHandler = { uri, inputFileSize, outputFileSize ->
                    totalInputFileSize += inputFileSize
                    totalOutputFileSize += outputFileSize
                    compressedFiles.add(uri)
                    iteratorFunction(i+1, false) // call to self with error flag false
                })
            }
            if (i >= inputFilesCount || error) { // end of iterations
                // show compression percentage as toast message if there was no error
                if (settings.showStatusMessages && !error) {
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
        }
        iteratorFunction(0, false) // start iterations
    }

}
