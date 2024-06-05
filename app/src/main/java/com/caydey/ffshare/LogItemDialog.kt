package com.caydey.ffshare

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.caydey.ffshare.utils.logs.Log

class LogItemDialog(context: Context, private val log: Log) : Dialog(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.log_item_dialog)
        
        val txtFfmpegOutput = findViewById<TextView>(R.id.logFfmpegOutput)
        val txtFfmpegTitle = findViewById<TextView>(R.id.logFfmpegCommand)

        // scrollbars
        txtFfmpegOutput.movementMethod = ScrollingMovementMethod()
        txtFfmpegOutput.setHorizontallyScrolling(true)

        // content
        txtFfmpegTitle.text = log.command
        txtFfmpegOutput.text = log.ffmpeg_output

        findViewById<Button>(R.id.logCopyButton).setOnClickListener {
            val myClipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("",log.getLogAsCopy())
            myClipboard.setPrimaryClip(clipData)

            Toast.makeText(context, context.getString(R.string.log_copied_toast), Toast.LENGTH_LONG).show()
        }
    }
}