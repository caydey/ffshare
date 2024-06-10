package com.caydey.ffshare

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.caydey.ffshare.databinding.ActivityLogsBinding
import com.caydey.ffshare.utils.Settings
import com.caydey.ffshare.utils.logs.Log
import com.caydey.ffshare.utils.logs.LogsDbHelper
import timber.log.Timber

class LogsActivity : AppCompatActivity() {
    private val logsDbHelper by lazy { LogsDbHelper(applicationContext) }
    private val settings: Settings by lazy { Settings(applicationContext) }

    private lateinit var adapter: LogItemsAdapter
    private lateinit var logs: ArrayList<Log>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        // show toolbar
        val binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Logs
        val listView = findViewById<ListView>(R.id.logsView)
        logs = logsDbHelper.getLogs()
        adapter = LogItemsAdapter(this, logs)

        // Show message if logs are disabled
        if (logs.isEmpty() && !settings.saveLogs) {
            findViewById<TextView>(R.id.txtLogsDisabled).visibility = View.VISIBLE
            val enableLogsBtn = findViewById<Button>(R.id.btnEnableLogs)
            enableLogsBtn.visibility = View.VISIBLE
            enableLogsBtn.setOnClickListener {
                settings.saveLogs = true
                recreate()
            }
        }

        // show log dialog on click
        listView.setOnItemClickListener { _, _, position, _ ->
            Timber.d("click %s", adapter.getItem(position)!!.inputFileName)
            val logDialog = LogItemDialog(this, adapter.getItem(position)!!)
            logDialog.show()
        }

        listView.adapter = adapter
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        // update
        logs = logsDbHelper.getLogs()
        adapter.notifyDataSetChanged()
        super.onStart()
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_logs, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete_logs -> {
                logsDbHelper.deleteLogs()
                logs = ArrayList() // empty array
                adapter.clear() // clear view
                adapter.notifyDataSetChanged() // refresh view
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
