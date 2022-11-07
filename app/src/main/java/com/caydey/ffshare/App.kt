package com.caydey.ffshare

import androidx.multidex.MultiDexApplication
import timber.log.Timber

class App: MultiDexApplication() {
    private val settingsVersionUpdater = SettingsVersionUpdater(this)
    override fun onCreate() {
        super.onCreate()

        // check if there has been a version change and if it requires the settings to be changed
        settingsVersionUpdater.check()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}