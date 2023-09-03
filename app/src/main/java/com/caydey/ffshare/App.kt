package com.caydey.ffshare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.multidex.MultiDexApplication
import com.caydey.ffshare.services.MEDIA_NOTIFICATION_CHANNEL
import timber.log.Timber

class App : MultiDexApplication() {
    companion object {
        var versionName = ""
    }

    private val settingsVersionUpdater = SettingsVersionUpdater(this)
    override fun onCreate() {
        super.onCreate()

        // save version name as static variable for use with Log class and MainActivity classes
        @Suppress("DEPRECATION")
        versionName = packageManager.getPackageInfo(applicationContext.packageName, 0).versionName

        // check if there has been a version change and if it requires the settings to be changed
        settingsVersionUpdater.check()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize notification channel
        initializeNotificationChannel()
    }

    private fun initializeNotificationChannel() {
        val channel = NotificationChannel(
            MEDIA_NOTIFICATION_CHANNEL,
            getString(R.string.media_service_notification_title),
            NotificationManager.IMPORTANCE_HIGH
        )

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}