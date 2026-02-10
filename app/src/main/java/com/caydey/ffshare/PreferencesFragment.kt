package com.caydey.ffshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.*

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        dynamicallyShowCustomName()
        dynamicallyAddCustomParamTooltips()
        setupAboutLinks()
    }

    private fun setupAboutLinks() {
        findPreference<Preference>("pref_about_source")?.setOnPreferenceClickListener {
            openUrl("https://github.com/caydey/ffshare")
            true
        }
        findPreference<Preference>("pref_about_inspiration")?.setOnPreferenceClickListener {
            openUrl("https://gitlab.com/juanitobananas/scrambled-exif")
            true
        }
        findPreference<Preference>("pref_about_ffmpeg")?.setOnPreferenceClickListener {
            openUrl("https://github.com/arthenica/ffmpeg-kit")
            true
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
    private fun dynamicallyAddCustomParamTooltips() {
        val customParamKeys = arrayOf("pref_custom_video_params", "pref_custom_audio_params", "pref_custom_image_params")
        for (customParamKey in customParamKeys) {
            val element = findPreference<EditTextPreference>(customParamKey)
            element?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        }
    }
    private fun dynamicallyShowCustomName() {
        // only show pref_compressed_media_custom_name if pref_compressed_media_name is "Custom"
        val customMediaNamePreference = findPreference<EditTextPreference>("pref_compressed_media_custom_name")
        val compressedMediaNamePreference = findPreference<ListPreference>("pref_compressed_media_name")
        compressedMediaNamePreference?.setOnPreferenceChangeListener { _, value ->
            customMediaNamePreference?.isVisible = (value == "CUSTOM")
            true
        }
        // trigger update for initial load
        compressedMediaNamePreference?.callChangeListener(compressedMediaNamePreference.value)
    }
}