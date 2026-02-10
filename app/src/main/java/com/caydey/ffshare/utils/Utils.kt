package com.caydey.ffshare.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import com.caydey.ffshare.extensions.mediaCacheDir
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

        var filename = getRandomFilename(fileExtension); // UUID
        if (settings.compressedMediaName == Settings.CompressedMediaNameOpts.ORIGINAL) {
            // if getFilenameFromUri returns null default to randomFilename
            filename = getFilenameFromUri(uri) ?: getRandomFilename(fileExtension)
            filename = filename.substringBeforeLast(".")+".${fileExtension.name.lowercase()}"
        } else if (settings.compressedMediaName == Settings.CompressedMediaNameOpts.CUSTOM) {
           filename = "${settings.compressedMediaCustomName}.${fileExtension.name.lowercase()}"
        }
        Timber.d("Created output file '%s'", filename)
        val outputFile = File(makeCacheUUIDFolder(), filename)
        return Pair(outputFile, fileExtension)
    }
    private fun getOutputFileMediaType(inputFileMediaType: MediaType): MediaType {
        var outputMediaType: MediaType = MediaType.UNKNOWN
        if (isVideo(inputFileMediaType)) {
            outputMediaType = settings.videoConversionOutput;
        } else if (isImage(inputFileMediaType)) {
            if (settings.treatGifsAsVideos && inputFileMediaType == MediaType.GIF) {
                outputMediaType = settings.videoConversionOutput
            } else {
                outputMediaType = settings.imageConversionOutput
            }
        } else if (isAudio(inputFileMediaType)) {
            outputMediaType = settings.audioConversionOutput
        }

        if (outputMediaType == MediaType.UNKNOWN) {
            outputMediaType = inputFileMediaType;
        }
        return outputMediaType
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
                "wav" to MediaType.WAV,
                "opus" to MediaType.OPUS
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
        JPEG, JPG, PNG, GIF, WEBP, // images
        MP3, OGG, AAC, WAV, OPUS, // audios
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
                || type == MediaType.WAV || type == MediaType.OPUS
    }

    fun getAllowedMimes(): Array<String> {
        if (BuildConfig.FLAVOR === "full")
            return arrayOf("audio/*","image/*", "video/*")
        return arrayOf("image/*", "video/*")
    }

    /**
     * Copies a file to Downloads/ffshare folder.
     * Returns the Uri of the copied file, or null if failed.
     */
    fun copyToDownloads(sourceFile: File, mediaType: MediaType): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                copyToDownloadsMediaStore(sourceFile, mediaType)
            } else {
                // Older versions use direct file access
                copyToDownloadsLegacy(sourceFile)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy file to Downloads")
            null
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun copyToDownloadsMediaStore(sourceFile: File, mediaType: MediaType): Uri? {
        val mimeType = getMimeType(mediaType)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/ffshare"

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
        }

        Timber.d("Copied ${sourceFile.name} to Downloads/ffshare")
        return uri
    }

    private fun copyToDownloadsLegacy(sourceFile: File): Uri? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val ffshareDir = File(downloadsDir, "ffshare")
        if (!ffshareDir.exists()) {
            ffshareDir.mkdirs()
        }

        val destFile = File(ffshareDir, sourceFile.name)
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        Timber.d("Copied ${sourceFile.name} to Downloads/ffshare")
        return Uri.fromFile(destFile)
    }

    private fun getMimeType(mediaType: MediaType): String {
        return when (mediaType) {
            MediaType.MP4 -> "video/mp4"
            MediaType.MKV -> "video/x-matroska"
            MediaType.WEBM -> "video/webm"
            MediaType.AVI -> "video/x-msvideo"
            MediaType.JPEG, MediaType.JPG -> "image/jpeg"
            MediaType.PNG -> "image/png"
            MediaType.GIF -> "image/gif"
            MediaType.WEBP -> "image/webp"
            MediaType.MP3 -> "audio/mpeg"
            MediaType.OGG -> "audio/ogg"
            MediaType.AAC -> "audio/aac"
            MediaType.WAV -> "audio/wav"
            MediaType.OPUS -> "audio/opus"
            else -> "application/octet-stream"
        }
    }
}