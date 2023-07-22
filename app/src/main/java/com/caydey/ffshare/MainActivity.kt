package com.caydey.ffshare

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.caydey.ffshare.databinding.ActivityMainBinding
import com.caydey.ffshare.utils.Utils


class MainActivity : AppCompatActivity() {

    private val utils: Utils by lazy { Utils(applicationContext) }

    private val selectedFileLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        // exited from file picker without selecting a file
        if (it.isEmpty()) {
            return@registerForActivityResult
        }

        val intent = Intent(this, HandleMediaActivity::class.java)
        val uris = ArrayList<Parcelable>(it) // convert List<> to ArrayList<> for intent

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)

        // always send multiple since sending an array (uris)
        intent.action = Intent.ACTION_SEND_MULTIPLE

        // simulate clicking share button
        startActivity(intent)
    }
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

        // Select File button listener
        findViewById<Button>(R.id.btnSelectFile).setOnClickListener {
            selectedFileLauncher.launch(utils.getAllowedMimes())
        }
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
