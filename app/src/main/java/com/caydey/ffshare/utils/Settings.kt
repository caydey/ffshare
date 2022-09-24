package com.caydey.ffshare.utils

import android.content.Context
import android.content.SharedPreferences

import androidx.preference.PreferenceManager

class Settings(private val context: Context) {
    enum class CompressedMediaNameOpts {
        ORIGINAL,
        UUID,
        CUSTOM
    }

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    var compressedMediaName: CompressedMediaNameOpts
        get() {
            // 2nd param is the default value
            val preferencesString = preferences.getString(COMPRESSED_MEDIA_NAME, CompressedMediaNameOpts.UUID.name)!!
            return CompressedMediaNameOpts.valueOf(preferencesString) // convert string to enum
        }
        set(value) = setPreference(COMPRESSED_MEDIA_NAME, value.toString())

    var compressedMediaCustomName: String
        get() = preferences.getString(COMPRESSED_MEDIA_CUSTOM_NAME, "")!!
        set(value) = setPreference(COMPRESSED_MEDIA_CUSTOM_NAME, value)

    var convertVideosToMp4: Boolean
        get() = preferences.getBoolean(CONVERT_VIDEOS_TO_MP4, true)
        set(value) = setPreference(CONVERT_VIDEOS_TO_MP4, value)

    var convertGifToMp4: Boolean
        get() = preferences.getBoolean(CONVERT_GIF_TO_MP4, false)
        set(value) = setPreference(CONVERT_GIF_TO_MP4, value)

    var showStatusMessages: Boolean
        get() = preferences.getBoolean(SHOW_STATUS_MESSAGES, true)
        set(value) = setPreference(SHOW_STATUS_MESSAGES, value)

    var videoCrf: Int
        get() = preferences.getString(VIDEO_CRF, "23")!!.toInt()
        set(value) = setPreference(VIDEO_CRF, value)

    var jpegQscale: Int
        get() = preferences.getString(JPEG_QSCALE, "10")!!.toInt()
        set(value) = setPreference(JPEG_QSCALE, value)

    var videoMaxBitrate: String
        get() = preferences.getString(VIDEO_MAX_BITRATE, "2M")!!
        set(value) = setPreference(VIDEO_MAX_BITRATE, value)

    var maxResolution: Int
        get() = preferences.getString(MAX_RESOLUTION, "1080")!!.toInt()
        set(value) = setPreference(MAX_RESOLUTION, value)

    var copyExifTags: Boolean
        get() = preferences.getBoolean(COPY_EXIF_TAGS, false)
        set(value) = setPreference(COPY_EXIF_TAGS, value)


    private fun setPreference(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
    private fun setPreference(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
    private fun setPreference(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    // static variables
    companion object {
        const val COMPRESSED_MEDIA_NAME = "pref_compressed_media_name"
        const val COMPRESSED_MEDIA_CUSTOM_NAME = "pref_compressed_media_custom_name"
        const val CONVERT_VIDEOS_TO_MP4 = "pref_convert_videos_to_mp4"
        const val CONVERT_GIF_TO_MP4 = "pref_convert_gif_to_mp4"
        const val SHOW_STATUS_MESSAGES = "pref_show_status_messages"
        const val VIDEO_CRF = "pref_video_crf"
        const val JPEG_QSCALE = "pref_jpeg_qscale"
        const val VIDEO_MAX_BITRATE = "pref_video_max_bitrate"
        const val MAX_RESOLUTION = "pref_max_resolution"
        const val COPY_EXIF_TAGS = "pref_copy_exif_tags"
    }
}