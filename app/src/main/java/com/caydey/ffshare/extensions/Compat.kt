package com.caydey.ffshare.extensions
import android.content.Intent
import android.os.Build
import android.os.Parcelable

// https://stackoverflow.com/questions/73019160/android-getparcelableextra-deprecated
inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
    when {
        Build.VERSION.SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
        else ->
            @Suppress("deprecation")
            getParcelableExtra(key)
                    as? T
    }

inline fun <reified T : Parcelable> Intent.parcelableArrayList(key: String): ArrayList<T>? =
    when {
        Build.VERSION.SDK_INT >= 33 -> getParcelableArrayListExtra(key, T::class.java)
        else ->
            @Suppress("deprecation")
            getParcelableArrayListExtra(key)
    }
