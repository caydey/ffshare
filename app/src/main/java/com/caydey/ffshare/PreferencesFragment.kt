package com.caydey.ffshare

import android.os.Bundle
import androidx.preference.*

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        dynamicallyShowCustomName();
        dynamicallyAddCustomParamTooltips();
    }
    private fun dynamicallyAddCustomParamTooltips() {
        val customParamKeys = arrayOf("pref_custom_video_params", "pref_custom_audio_params", "pref_custom_image_params");
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