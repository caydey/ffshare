package com.caydey.ffshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PreferencesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        // change title from 'FFShare' to 'Settings'
        supportActionBar?.title = getString(R.string.menu_item_settings)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.action_settings, PreferencesFragment())
            .commit()
    }
}