package com.caydey.ffshare

import android.app.Application
import timber.log.Timber

class App: Application() {
    companion object {
        var versionName = ""
    }
    private val settingsVersionUpdater = SettingsVersionUpdater(this)
    override fun onCreate() {
        super.onCreate()

        // save version name as static variable for use with Log class and MainActivity classes
        @Suppress("DEPRECATION")
        versionName = packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: ""

        // check if there has been a version change and if it requires the settings to be changed
        settingsVersionUpdater.check()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}