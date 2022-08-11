package com.caydey.ffshare.extensions

import android.content.Context
import java.io.File

val Context.mediaCacheDir: File
    get() {
        File(cacheDir, "media").mkdirs()
        return File(cacheDir, "media")
    }
