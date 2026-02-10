package com.caydey.ffshare.service

import android.net.Uri

sealed class EncodingState {
    object Idle : EncodingState()

    data class Encoding(
        val currentFile: Int,
        val totalFiles: Int,
        val fileName: String,
        val progressPercent: Float,
        val processedTime: Int,      // milliseconds
        val totalDuration: Int,      // milliseconds
        val estimatedTimeRemaining: Long,  // milliseconds
        val outputSize: Long         // bytes
    ) : EncodingState()

    data class Completed(
        val outputUris: ArrayList<Uri>,
        val totalInputSize: Long,
        val totalOutputSize: Long
    ) : EncodingState()

    data class Failed(
        val error: String
    ) : EncodingState()

    object Cancelled : EncodingState()
}
