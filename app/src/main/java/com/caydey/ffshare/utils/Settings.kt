package com.caydey.ffshare.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore.Video

import androidx.preference.PreferenceManager

class Settings(private val context: Context) {
    enum class CompressedMediaNameOpts {
        ORIGINAL,
        UUID,
        CUSTOM
    }

    enum class VideoCodecOpts(val raw: String) {
        LIBX264("libx264"),
        LIBX265("libx265"),
        MPEG4("mpeg4")
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

    var convertAudiosToMp3: Boolean
        get() = preferences.getBoolean(CONVERT_AUDIOS_TO_MP3, true)
        set(value) = setPreference(CONVERT_AUDIOS_TO_MP3, value)

    var convertImagesToJpg: Boolean
        get() = preferences.getBoolean(CONVERT_IMAGES_TO_JPG, true)
        set(value) = setPreference(CONVERT_IMAGES_TO_JPG, value)

    var convertGifToMp4: Boolean
        get() = preferences.getBoolean(CONVERT_GIF_TO_MP4, false)
        set(value) = setPreference(CONVERT_GIF_TO_MP4, value)

    var showStatusMessages: Boolean
        get() = preferences.getBoolean(SHOW_STATUS_MESSAGES, true)
        set(value) = setPreference(SHOW_STATUS_MESSAGES, value)

    var videoCrf: Int
        get() = preferences.getString(VIDEO_CRF, "23")!!.toInt()
        set(value) = setPreference(VIDEO_CRF, value.toString())

    var jpegQscale: Int
        get() = preferences.getString(JPEG_QSCALE, "10")!!.toInt()
        set(value) = setPreference(JPEG_QSCALE, value.toString())

    var videoMaxFileSize: Int
        get() = preferences.getString(VIDEO_MAX_FILE_SIZE, "0")!!.toInt()
        set(value) = setPreference(VIDEO_MAX_FILE_SIZE, value.toString())

    var maxVideoResolution: Int
        get() = preferences.getString(MAX_VIDEO_RESOLUTION, "1080")!!.toInt()
        set(value) = setPreference(MAX_VIDEO_RESOLUTION, value.toString())

    var maxImageResolution: Int
        get() = preferences.getString(MAX_IMAGE_RESOLUTION, "2160")!!.toInt()
        set(value) = setPreference(MAX_IMAGE_RESOLUTION, value.toString())

    var copyExifTags: Boolean
        get() = preferences.getBoolean(COPY_EXIF_TAGS, false)
        set(value) = setPreference(COPY_EXIF_TAGS, value)

    var lastVersion: Int
        get() = preferences.getInt(LAST_VERSION, 0)
        set(value) = setPreference(LAST_VERSION, value)

    var saveLogs: Boolean
        get() = preferences.getBoolean(SAVE_LOGS, true)
        set(value) = setPreference(SAVE_LOGS, value)

    var compressionPreset: String
        get() = preferences.getString(COMPRESSION_PRESET, "medium")!!
        set(value) = setPreference(COMPRESSION_PRESET, value)

    var videoCodec: VideoCodecOpts
        get() {
            val videoCodecString = preferences.getString(VIDEO_CODEC, VideoCodecOpts.LIBX264.name)!!
            return VideoCodecOpts.valueOf(videoCodecString) // convert string to enum
        }
        set(value) = setPreference(VIDEO_CODEC, value.toString())

    var customVideoParams: String
        get() = preferences.getString(CUSTOM_VIDEO_PARAMS, "")!!
        set(value) = setPreference(CUSTOM_VIDEO_PARAMS, value)

    var customImageParams: String
        get() = preferences.getString(CUSTOM_IMAGE_PARAMS, "")!!
        set(value) = setPreference(CUSTOM_IMAGE_PARAMS, value)

    var customAudioParams: String
        get() = preferences.getString(CUSTOM_AUDIO_PARAMS, "")!!
        set(value) = setPreference(CUSTOM_AUDIO_PARAMS, value)

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
        const val CONVERT_AUDIOS_TO_MP3 = "pref_convert_audios_to_mp3"
        const val CONVERT_IMAGES_TO_JPG = "pref_convert_images_to_jpg"
        const val CONVERT_GIF_TO_MP4 = "pref_convert_gif_to_mp4"
        const val SHOW_STATUS_MESSAGES = "pref_show_status_messages"
        const val VIDEO_CRF = "pref_video_crf"
        const val JPEG_QSCALE = "pref_jpeg_qscale"
        const val VIDEO_MAX_FILE_SIZE = "pref_video_max_file_size"
        const val MAX_VIDEO_RESOLUTION = "pref_max_video_resolution"
        const val MAX_IMAGE_RESOLUTION = "pref_max_image_resolution"
        const val COPY_EXIF_TAGS = "pref_copy_exif_tags"
        const val LAST_VERSION = "pref_last_version"
        const val SAVE_LOGS = "pref_save_logs"
        const val COMPRESSION_PRESET = "pref_compression_preset"
        const val VIDEO_CODEC = "pref_video_codec"
        const val CUSTOM_VIDEO_PARAMS = "pref_custom_video_params"
        const val CUSTOM_IMAGE_PARAMS = "pref_custom_image_params"
        const val CUSTOM_AUDIO_PARAMS = "pref_custom_audio_params"
    }
}