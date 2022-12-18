package com.caydey.ffshare

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.caydey.ffshare.utils.Utils
import com.caydey.ffshare.utils.logs.Log
import java.time.*
import java.util.*
import kotlin.collections.ArrayList

class LogItemsAdapter(private val _context: Context, logs: ArrayList<Log>) :
    ArrayAdapter<Log>(_context, R.layout.log_item_view, logs) {
    private val utils: Utils by lazy { Utils(context) }

    @SuppressLint("SimpleDateFormat")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if(convertView == null)
        {
            val inflater = (_context as Activity).layoutInflater
            val newConvertView = inflater.inflate(R.layout.log_item_view, parent,false)

            val focusLog = getItem(position)
            if (focusLog != null) {
                // set text values
                newConvertView.findViewById<TextView>(R.id.logDate).text = focusLog.getFormattedTime()
                newConvertView.findViewById<TextView>(R.id.logFilename).text = focusLog.inputFileName
                newConvertView.findViewById<TextView>(R.id.logInputSize).text = utils.bytesToHuman(focusLog.inputSize)
                newConvertView.findViewById<TextView>(R.id.logCompressionPercent).text = context.getString(R.string.format_percentage,(1-(focusLog.outputSize.toDouble() / focusLog.inputSize))*100.0)

                // set background color to red if failed
                if (!focusLog.successful) {
                    newConvertView.findViewById<ConstraintLayout>(R.id.logLayout).setBackgroundColor(context.getColor(R.color.failed))
                }
            }
            return newConvertView
        }

        return convertView
    }
}