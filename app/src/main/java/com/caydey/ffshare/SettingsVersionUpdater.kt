package com.caydey.ffshare

import android.content.Context
import com.caydey.ffshare.utils.Settings
import timber.log.Timber

class SettingsVersionUpdater(private val context: Context) {
    private val settings: Settings by lazy { Settings(context) }

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
        // Version 10 (1.2.2) changes pref_video_max_file_size to store value as kib instead of mib
        // Affects versions 6 (1.1.3) and later
        if ((latest in 6..9 || latest == 0) && current >= 10) {
            videoMaxFileSizeMibToKib()
        }
    }

    private fun videoMaxFileSizeMibToKib() {
        Timber.d("converting video max file size from Mib to Kib")
        val maxFileSize = settings.videoMaxFileSize
        // convert to kib
        settings.videoMaxFileSize = maxFileSize * 1024
    }
}