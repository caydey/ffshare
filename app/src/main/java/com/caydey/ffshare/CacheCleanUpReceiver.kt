package com.caydey.ffshare

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import java.io.File

class CacheCleanUpReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Cleaning up cache")
        val mediaDir = File(context.cacheDir, "media")
        val cacheFiles = mediaDir.listFiles()

        cacheFiles?: return

        for (cacheFile in cacheFiles) {
            // media file older that 1 hour
            if (System.currentTimeMillis() - cacheFile.lastModified() > HOUR) {
                Timber.d("Deleting file '%s'", cacheFile)
                cacheFile.delete()
            }
        }

        // re-scan directory checking if it is empty
        if (mediaDir.listFiles()?.isEmpty() == true) {
            Timber.d("Cache folder empty, canceling cleanup alarm")
            // cancel alarm scheduler as there is no more cache files to cleanup
            val sender = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            alarmManager?.cancel(sender)
        }
    }
    companion object {
        private const val HOUR = 60 * 60 * 1_000
    }
}