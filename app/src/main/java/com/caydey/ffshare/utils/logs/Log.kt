package com.caydey.ffshare.utils.logs

import android.annotation.SuppressLint
import com.caydey.ffshare.App
import java.text.SimpleDateFormat
import java.util.*

class Log(
    val time: Long,
    val command: String,
    val inputFileName: String,
    val outputFileName: String,
    val successful: Boolean,
    val ffmpeg_output: String,
    val inputSize: Long,
    val outputSize: Long,
    val appVersion: String
) {
    constructor(command: String, inputFileName: String, outputFileName: String, successful: Boolean, ffmpeg_output: String, inputSize: Long, outputSize: Long): this(
        System.currentTimeMillis(),
        command,
        inputFileName,
        outputFileName,
        successful,
        ffmpeg_output,
        inputSize,
        outputSize,
        App.versionName
    )
    @SuppressLint("SimpleDateFormat")
    fun getFormattedTime(): String {
        // date
        val formatter = SimpleDateFormat("yy/MM/dd HH:mm:ss")
        val dateTime = Date(time)
        return formatter.format(dateTime)
    }
    fun getLogAsCopy(): String {
        // Command:
        //
        // Output:
        //
        // Result: SUCCESS|FAILURE
        // App Version: x.x.x

        val builder = StringBuilder()

        builder.append("Command:\n")
        builder.append(command)
        builder.append("\n")

        builder.append("Output:\n")
        builder.append(ffmpeg_output)
        builder.append("\n")

        builder.append("Result: ")
        if (successful) {
            builder.append("SUCCESS")
        } else {
            builder.append("FAILURE")
        }

        builder.append("\n")
        builder.append("App Version: $appVersion")

        return builder.toString()
    }
}