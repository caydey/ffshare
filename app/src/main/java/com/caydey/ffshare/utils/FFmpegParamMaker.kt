package com.caydey.ffshare.utils

import android.net.Uri
import com.antonkarpenko.ffmpegkit.MediaInformation
import timber.log.Timber
import java.util.StringJoiner
import kotlin.math.ceil

class FFmpegParamMaker(val settings: Settings, val utils: Utils) {
    /**
     * Creates FFmpeg parameters for encoding.
     *
     * @param inputFile The input file URI
     * @param mediaInformation Media information from FFprobe
     * @param mediaType The input media type
     * @param outputMediaType The output media type
     * @return The FFmpeg parameters string
     */
    fun create(
        inputFile: Uri,
        mediaInformation: MediaInformation,
        mediaType: Utils.MediaType,
        outputMediaType: Utils.MediaType
    ): String {
        // custom params
        if (utils.isImage(outputMediaType) && settings.customImageParams.isNotEmpty()) {
            return settings.customImageParams
        }
        if (utils.isVideo(outputMediaType) && settings.customVideoParams.isNotEmpty()) {
            return settings.customVideoParams
        }
        if (utils.isAudio(outputMediaType) && settings.customAudioParams.isNotEmpty()) {
            return settings.customAudioParams
        }

        val params = StringJoiner(" ")
        val videoFormatParams = StringJoiner(",")

        // preset - webp doesn't support this
        if (outputMediaType != Utils.MediaType.WEBP) {
            params.add("-preset ${settings.compressionPreset}")
        }

        if (utils.isAudio(mediaType) || utils.isVideo(mediaType)) {
            // add audio codec
            if (settings.audioCodec != Settings.AudioCodecOpts.DEFAULT) {
                params.add("-c:a ${settings.audioCodec.raw}")
            }
        }

        var videoScaleApplied = false
        // videos and images max resolution
        if (utils.isVideo(mediaType) || utils.isImage(mediaType)) {
            val (resolutionWidth, resolutionHeight) = utils.getMediaResolution(inputFile, mediaType)
            val isPortrait = resolutionHeight > resolutionWidth
            val resolution = if (isPortrait) resolutionWidth else resolutionHeight
            val maxResolution = if (utils.isImage(mediaType)) settings.maxImageResolution else settings.maxVideoResolution
            // only reduce resolution
            if (resolution > maxResolution && maxResolution != 0) {
                videoScaleApplied = true
                var pixelRounding = "-1"
                // H.26x videos require dimensions to be divisible by 2
                if (utils.isVideo(outputMediaType) && settings.videoCodec in setOf(Settings.VideoCodecOpts.H264, Settings.VideoCodecOpts.H265)) {
                    pixelRounding = "-2"
                }
                if (isPortrait) {
                    videoFormatParams.add("scale=$maxResolution:$pixelRounding,setsar=1")
                } else {
                    videoFormatParams.add("scale=$pixelRounding:$maxResolution,setsar=1")
                }
            }
        }

        // video
        if (utils.isVideo(outputMediaType)) { // check outputMediaType not mediaType because conversions
            // pixel format - convert to yuv420p and normalize color properties
            // This handles both standard and HDR content by normalizing to bt709
            videoFormatParams.add("format=yuv420p,colorspace=bt709:iall=bt709:fast=1")

            // video codec
            if (settings.videoCodec != Settings.VideoCodecOpts.DEFAULT) {
                params.add("-c:v ${settings.videoCodec.raw}")
            } else {
                // When DEFAULT, explicitly use libx264 to avoid hardware encoder issues with HDR
                params.add("-c:v libx264")
            }

            // crf
            params.add("-crf ${settings.videoCrf}")

            // GOP size - set to 2x framerate for better seeking and encoder stability
            // This prevents warnings about i-frame-interval with mediacodec
            val fps = mediaInformation.streams.firstOrNull()?.averageFrameRate?.toIntOrNull() ?: 30
            val gopSize = fps * 2
            params.add("-g $gopSize")

            // H.26x requires dimensions to be divisible by 2, video scaling will account for this if applied
            if (!videoScaleApplied) {
                val stream = mediaInformation.streams[0]
                if (stream?.width?.rem(2) != 0L || stream.height?.rem(2) != 0L) {
                    videoFormatParams.add("crop=trunc(iw/2)*2:trunc(ih/2)*2")
                    // could also use "pad=ceil(iw/2)*2:ceil(ih/2)*2" to add column/row of black pixels
                }
            }

            //  max file size (limit the bitrate to achieve this)
            if (settings.videoMaxFileSize != 0) {
                // ffmpeg does not like -maxrate & -bufsize params when the output file is webm
                if (outputMediaType != Utils.MediaType.WEBM) {
                    // Calculate maximum bitrate in kbps.
                    // Ceil duration to ensure the maximum is strict. toInt to floor result, ffmpeg takes ints.
                    val maxBitrate = (settings.videoMaxFileSize * 8 / ceil(mediaInformation.duration.toFloat())).toInt()
                    Timber.d("Maximum bitrate for targeted filesize (%dK): %dk", settings.videoMaxFileSize, maxBitrate)

                    // audio can have at most one third of the total bitrate
                    val audioSplit = maxBitrate / 3
                    // round audio bitrate down to 192,128,96,64,32,24
                    val audioBitrate = if (audioSplit > 192) 192 // maximum audio bitrate is 192k
                    else if (audioSplit > 128) 128
                    else if (audioSplit > 96) 96
                    else if (audioSplit > 64) 64
                    else if (audioSplit > 32) 32
                    else 24 // minimum audio bitrate is 24k

                    // set audio bitrate
                    params.add("-b:a ${audioBitrate}k")

                    // set max video bitrate
                    val videoBitrate = (maxBitrate - audioBitrate)
                    params.add("-maxrate ${videoBitrate}k -bufsize ${videoBitrate}k")
                }
            }
        }

        if (videoFormatParams.length() > 0) {
            params.add("-vf \"$videoFormatParams\"")
        }

        // jpeg quality
        if (outputMediaType == Utils.MediaType.JPEG || outputMediaType == Utils.MediaType.JPG) {
            params.add("-qscale:v ${settings.jpegQscale}")
        }

        return params.toString()
    }
}
