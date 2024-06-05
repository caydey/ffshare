package com.caydey.ffshare.utils

import android.content.Context
import androidx.core.app.ActivityCompat
import com.caydey.ffshare.extensions.mediaCacheDir
import java.io.File
import java.util.*
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.caydey.ffshare.BuildConfig
import timber.log.Timber
import java.io.InputStream
import kotlin.math.ln
import kotlin.math.pow


const val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1000
class Utils(private val context: Context) {
    private val settings: Settings by lazy { Settings(context) }

    fun getCacheOutputFile(uri: Uri, mediaType: MediaType): Pair<File, MediaType> {
        val fileExtension = getOutputFileMediaType(mediaType) // some files are converted to different formats e.g. mkv to mp4
        val filename: String = when (settings.compressedMediaName) {
            Settings.CompressedMediaNameOpts.ORIGINAL -> getFilenameFromUri(uri) ?: getRandomFilename(fileExtension) // if getFilenameFromUri returns null default to randomFilename
            Settings.CompressedMediaNameOpts.UUID -> getRandomFilename(fileExtension)
            Settings.CompressedMediaNameOpts.CUSTOM -> "${settings.compressedMediaCustomName}.${fileExtension.name.lowercase()}"
        }
        Timber.d("Created output file '%s'", filename)
        val outputFile = File(makeCacheUUIDFolder(), filename)
        return Pair(outputFile, fileExtension)
    }
    private fun getOutputFileMediaType(inputFileMediaType: MediaType): MediaType {
        // change extension if settings wants
        if (settings.convertVideosToMp4) {
            if (isVideo(inputFileMediaType)) {
                return MediaType.MP4
            }
        }
        if (settings.convertAudiosToMp3) {
            if (isAudio(inputFileMediaType)) {
                return MediaType.MP3
            }
        }
        if (settings.convertGifToMp4) {
            if (inputFileMediaType == MediaType.GIF) {
                return MediaType.MP4
            }
        }
        if (settings.convertImagesToJpg) {
            if (isImage(inputFileMediaType)) {
                return MediaType.JPEG
            }
        }
        // no conversions
        return inputFileMediaType
    }
    private fun makeCacheUUIDFolder(): File {
        val cacheUUIDFolder = File(context.mediaCacheDir, UUID.randomUUID().toString())
        cacheUUIDFolder.mkdirs()
        return cacheUUIDFolder
    }

    private fun getRandomFilename(mediaType: MediaType): String {
        return "${UUID.randomUUID()}.${mediaType.name.lowercase()}"
    }
    fun getFilenameFromUri(uri: Uri): String? {
        var filename: String? = null
        val queryCursor = context.contentResolver.query(uri, null, null, null, null)
        queryCursor?.let { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    filename = cursor.getString(nameIndex)
                }
            }
            cursor.close()
        }
        return filename
    }

    val isReadPermissionGranted: Boolean
        get() {
            val check = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            return (check == PackageManager.PERMISSION_GRANTED)
        }
    fun requestReadPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
    }


    private fun getFileHexSignature(inputStream: InputStream): String {
        val arr = ByteArray(16)
        inputStream.read(arr)
        val builder = StringBuilder()
        for (byte in arr) { // foreach byte in head
            val hex = "%02X".format(byte) // format as hex
            builder.append(hex)
        }
        return builder.toString()
    }

    fun getMediaType(uri: Uri): MediaType {
        val filename: String? = getFilenameFromUri(uri)
        var mediaType: MediaType = MediaType.UNKNOWN

        // get type from file extension
        if (filename != null) {
            val extensionsToMediaType = mapOf(
                // images
                "jpg" to MediaType.JPEG,
                "jpeg" to MediaType.JPEG,
                "png" to MediaType.PNG,
                "gif" to MediaType.GIF,
                "webp" to MediaType.WEBP,
                // videos
                "mp4" to MediaType.MP4,
                "mkv" to MediaType.MKV,
                "webm" to MediaType.WEBM,
                "avi" to MediaType.AVI,
                "mov" to MediaType.MP4,
                // audios
                "mp3" to MediaType.MP3,
                "ogg" to MediaType.OGG,
                "aac" to MediaType.AAC,
                "wav" to MediaType.WAV
            )

            val extension = filename.substringAfterLast('.').lowercase()
            mediaType = extensionsToMediaType[extension] ?: MediaType.UNKNOWN
        }
        // unable to get filetype from filename extension, using signature detection
        // https://en.wikipedia.org/wiki/List_of_file_signatures
        if (mediaType == MediaType.UNKNOWN) {
            Timber.d("unable to find filetype from extension, trying file signature")
            val inputStream = context.contentResolver.openInputStream(uri)
            val signature = getFileHexSignature(inputStream!!)
            inputStream.close()

            if (signature.startsWith("FFD8FF")) {
                mediaType = MediaType.JPEG
            } else if (signature.startsWith("89504E470D0A1A0A")) {
                mediaType = MediaType.PNG
            } else if (signature.startsWith("47494638")) {
                mediaType = MediaType.GIF
            } else if (signature.startsWith("52494646") && signature.drop(16).startsWith("57454250")) { // 52 49 46 46 ?? ?? ?? ?? 57 45 42 50
                mediaType = MediaType.WEBP
            } else if (signature.drop(8).startsWith("6674797069736F6D")) { // ** ** ** ** 66 74 79 70 69 73 6F 6D
                mediaType = MediaType.MP4
            } else if (signature.startsWith("1A45DFA3")) { // or webm, but assume mkv, also not that big a deal as only happens when filename is not found
                mediaType = MediaType.MKV
            } else if (signature.startsWith("52494646") && signature.drop(16).startsWith("41564920")) { // 52 49 46 46 ** ** ** ** 41 56 49 20
                mediaType = MediaType.AVI
            } else if (signature.startsWith("494433") || signature.startsWith("FFFB")
                || signature.startsWith("FFF3") || signature.startsWith("FFF2")) {
                mediaType = MediaType.MP3
            } else if (signature.startsWith("4F676753")) {
                mediaType = MediaType.OGG
            } else if (signature.startsWith("52494646") && signature.drop(16).startsWith("57415645")) { // 52 49 46 46 ** ** ** ** 57 41 56 45
                mediaType = MediaType.WAV
            }
            if (mediaType == MediaType.UNKNOWN) {
                Timber.d("Unable to find filetype from signature")
            } else {
                Timber.d("Found Filetype from signature $mediaType")
            }
        } else {
            Timber.d("Found Filetype from extension $mediaType")
        }

        return mediaType
    }
    fun getMediaResolution(mediaUri: Uri, mediaType: MediaType): Pair<Int,Int> {
        // image
        if (isImage(mediaType)) {
            val bitmapOption = BitmapFactory.decodeStream(context.contentResolver.openInputStream(mediaUri)!!)
            return Pair(bitmapOption.width, bitmapOption.height)
        }
        // video
        // attempt to get video resolution
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, mediaUri)
        } catch (e: java.lang.RuntimeException) {
            return Pair(0,0) // unable to read video width/height, usually happens with avi files
        }
        // read video resolution
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        retriever.release()
        return Pair(width,height)
    }

    fun bytesToHuman(bytes: Long): String {
        // warning will break for files over 1 petabyte in size
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        val byte = 1024f
        if (bytes >= byte) {
            val e = (ln(bytes.toDouble()) / ln(byte)).toInt()
            val humanUnits = bytes / byte.pow(e)
            return String.format("%.01f %s", humanUnits, units[e])
        }
        return "$bytes ${units[0]}"
    }
    fun millisToMicrowaveTime(millis: Int): String {
        var remainder = millis / 1_000
        var time = ""
        for (i in 0..2) {
            val unit = remainder % 60
            remainder /= 60
            if (!(i == 2 && unit == 0)) { // exclude hours if there are none
                time = String.format("%02d:%s", unit, time)
            }
        }
        return time.dropLast(1)
    }

    enum class MediaType {
        MP4, MKV, WEBM, AVI, // videos
        JPEG, PNG, GIF, WEBP, // images
        MP3, OGG, AAC, WAV, // audios
        UNKNOWN
    }
    fun isSupportedMediaType(type: MediaType): Boolean {
        // obviously unsupported if MediaType isn't even known
        if (type == MediaType.UNKNOWN) return false

        // support if in "full" flavor
        if (BuildConfig.FLAVOR == "full") {
            return true
        }
        // video/image only flavor
        if (!isAudio(type)) { // if not audio then its supported
            return true
        }
        return false
    }
    fun isImage(type: MediaType): Boolean {
        return type == MediaType.JPEG || type == MediaType.PNG || type == MediaType.GIF
                || type == MediaType.WEBP
    }
    fun isVideo(type: MediaType): Boolean {
        return type == MediaType.MP4 || type == MediaType.MKV || type == MediaType.WEBM
                || type == MediaType.AVI
    }
    fun isAudio(type: MediaType): Boolean {
        return type == MediaType.MP3 || type == MediaType.OGG || type == MediaType.AAC
                || type == MediaType.WAV
    }

    fun getAllowedMimes(): Array<String> {
        if (BuildConfig.FLAVOR === "full")
            return arrayOf("audio/*","image/*", "video/*")
        return arrayOf("image/*", "video/*")
    }

}