package com.caydey.ffshare

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.caydey.ffshare.utils.Settings
import timber.log.Timber

class SettingsVersionUpdater(private val context: Context) {
    private val settings: Settings by lazy { Settings(context) }

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    fun check() {
        val lastVersion = settings.lastVersion
        val currentVersion = BuildConfig.VERSION_CODE

        if (lastVersion != currentVersion) {
            versionChange(lastVersion, currentVersion)
            // update latest version in preferences
            settings.lastVersion = currentVersion
        }
    }

    private fun versionChange(latest: Int, current: Int) {
        Timber.d("Version change %d => %d", latest, current)
        // latest == 0 because latest not implemented until 1.2.2 and if 0 means version is at most 1.2.2

        // Version 10 (1.2.2) changes pref_video_max_file_size to store value as kib instead of mib
        // Affects versions 6 (1.1.3) and later
        if ((latest in 6..9 || latest == 0) && current >= 10) {
            videoMaxFileSizeMibToKib()
        }

        // Version 15 (1.2.6) does not use pref_max_resolution, it uses pref_max_image_resolution & prev_max_video_resolution
        // Affects versions 6 (1.1.3) and later
        if ((latest in 6 ..14 || latest == 0) && current >= 15) {
            maxResolutionToVideoMaxResolution()
        }

        // Version 21 (1.3.2) refactored video codec in preferences from LIBX26* to H26*
        // Affects versions 19 (1.3.0) and later
        if ((latest in 19 .. 20) && current >= 21) {
            videoCodecPreferencesValueRefactor();
        }
    }

    private fun maxResolutionToVideoMaxResolution() {
        Timber.d("converting max resolution to max video/image resolution")
        val maxResolution = preferences.getString("pref_max_resolution", "0")
        preferences.edit().putString("pref_max_video_resolution", maxResolution).apply()
        preferences.edit().putString("pref_max_image_resolution", maxResolution).apply()
    }

    private fun videoMaxFileSizeMibToKib() {
        Timber.d("converting video max file size from Mib to Kib")
        val maxFileSize = settings.videoMaxFileSize
        // convert to kib
        settings.videoMaxFileSize = maxFileSize * 1024
    }

    private fun videoCodecPreferencesValueRefactor() {
        Timber.d("converting video codec settings value from libx26* to h26*")
        val oldVideoCodec = preferences.getString("pref_video_codec", Settings.VideoCodecOpts.DEFAULT.toString())

        val newVideoCodec = when (oldVideoCodec) {
            "LIBX264" -> "H264"
            "LIBX265" -> "H265"
            else -> null
        }
        if (newVideoCodec != null) {
            preferences.edit().putString("pref_video_codec", newVideoCodec).apply()
        }
    }
}