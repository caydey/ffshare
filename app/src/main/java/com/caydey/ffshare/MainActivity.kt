package com.caydey.ffshare

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caydey.ffshare.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val versionName = App.versionName
        findViewById<TextView>(R.id.lblVersion).text = getString(R.string.version, versionName)

        val flavorName = BuildConfig.FLAVOR
        findViewById<TextView>(R.id.lblFlavor).text = getString(R.string.flavor, flavorName)

        // allow clicking on links
        findViewById<TextView>(R.id.lblIntroductionLine0).movementMethod = LinkMovementMethod.getInstance()
        findViewById<TextView>(R.id.lblIntroductionLine1).movementMethod = LinkMovementMethod.getInstance()
        findViewById<TextView>(R.id.lblIntroductionLine2).movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(applicationContext, PreferencesActivity::class.java))
            R.id.action_history -> startActivity(Intent(applicationContext, LogsActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }
}
