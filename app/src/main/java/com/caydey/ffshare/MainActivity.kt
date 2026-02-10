package com.caydey.ffshare

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.caydey.ffshare.databinding.ActivityMainBinding
import com.caydey.ffshare.utils.Settings
import com.caydey.ffshare.utils.Utils
import com.caydey.ffshare.utils.logs.Log
import com.caydey.ffshare.utils.logs.LogsDbHelper


class MainActivity : AppCompatActivity() {

    private val utils: Utils by lazy { Utils(applicationContext) }
    private val settings: Settings by lazy { Settings(applicationContext) }
    private val logsDbHelper: LogsDbHelper by lazy { LogsDbHelper(applicationContext) }

    companion object {
        private const val MAX_RECENT_TASKS = 5
    }

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

        // allow clicking on links in credits
        findViewById<TextView>(R.id.lblCredits).movementMethod = LinkMovementMethod.getInstance()

        // Select File button listener
        findViewById<Button>(R.id.btnSelectFile).setOnClickListener {
            selectedFileLauncher.launch(utils.getAllowedMimes())
        }

        // View all tasks link
        findViewById<TextView>(R.id.lblViewAllTasks).setOnClickListener {
            startActivity(Intent(applicationContext, LogsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh recent tasks when returning to this activity
        loadRecentTasks()
    }

    private fun loadRecentTasks() {
        val recentTasksContainer = findViewById<LinearLayout>(R.id.recentTasksContainer)
        val noRecentTasksLabel = findViewById<TextView>(R.id.lblNoRecentTasks)

        recentTasksContainer.removeAllViews()

        // Only show if logs are enabled
        if (!settings.saveLogs) {
            noRecentTasksLabel.visibility = View.VISIBLE
            noRecentTasksLabel.text = getString(R.string.logs_disabled)
            return
        }

        val logs = logsDbHelper.getLogs()
        val recentLogs = logs.take(MAX_RECENT_TASKS)

        if (recentLogs.isEmpty()) {
            noRecentTasksLabel.visibility = View.VISIBLE
            noRecentTasksLabel.text = getString(R.string.recent_tasks_empty)
            return
        }

        noRecentTasksLabel.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (log in recentLogs) {
            val itemView = inflater.inflate(R.layout.recent_task_item, recentTasksContainer, false)
            bindRecentTaskItem(itemView, log)

            // Click listener to show log details
            itemView.setOnClickListener {
                val logDialog = LogItemDialog(this, log)
                logDialog.show()
            }

            recentTasksContainer.addView(itemView)
        }
    }

    private fun bindRecentTaskItem(view: View, log: Log) {
        // Status indicator color
        val statusIndicator = view.findViewById<View>(R.id.statusIndicator)
        if (log.successful) {
            statusIndicator.setBackgroundResource(R.drawable.status_dot_success)
        } else {
            statusIndicator.setBackgroundResource(R.drawable.status_dot_failure)
        }

        // File name
        view.findViewById<TextView>(R.id.txtFileName).text = log.inputFileName

        // Compression percentage
        val txtCompression = view.findViewById<TextView>(R.id.txtCompression)
        if (log.successful && log.inputSize > 0 && log.outputSize > 0) {
            val compressionPercent = (1 - (log.outputSize.toDouble() / log.inputSize)) * 100.0
            txtCompression.text = getString(R.string.format_percentage, compressionPercent)
            txtCompression.visibility = View.VISIBLE
        } else {
            txtCompression.visibility = View.GONE
        }

        // Details: input size -> output size, relative time
        val inputSizeStr = utils.bytesToHuman(log.inputSize)
        val timeStr = getRelativeTimeString(log.time)
        val detailsText = if (log.successful && log.outputSize > 0) {
            val outputSizeStr = utils.bytesToHuman(log.outputSize)
            "$inputSizeStr → $outputSizeStr • $timeStr"
        } else {
            "$inputSizeStr • $timeStr"
        }
        view.findViewById<TextView>(R.id.txtDetails).text = detailsText
    }

    private fun getRelativeTimeString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
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
