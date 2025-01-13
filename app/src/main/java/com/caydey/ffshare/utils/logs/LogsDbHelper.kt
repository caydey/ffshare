package com.caydey.ffshare.utils.logs

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import com.caydey.ffshare.utils.Settings
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class LogsDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val settings: Settings by lazy { Settings(context) }

    private var logCount: Int
    init {
        // get log count to limit them to $MAX_LOGS
        logCount = 0
        val cursor = readableDatabase.rawQuery("SELECT COUNT($COLUMN_NAME_ID) FROM $TABLE_NAME", null)
        cursor.moveToFirst()
        while(!cursor.isAfterLast) {
            logCount = cursor.getInt(0)
            cursor.moveToNext()
        }
        cursor.close()
    }

    // compression for ffmpeg output, Base64(GZIP($output))
    private fun gzipCompress(decompressed: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8).use { it.write(decompressed) }
        val ba = bos.toByteArray()
        return Base64.encodeToString(ba, 0).toString() // convert ByteArray to Base64 string
    }
    private fun gzipDecompress(compressed: String): String {
        val ba = Base64.decode(compressed, 0) // convert Base64 string to ByteArray
        val bais = ByteArrayInputStream(ba)
        GZIPInputStream(bais).bufferedReader(Charsets.UTF_8).use { return it.readText() }
    }

    fun getLogs(): ArrayList<Log> {
        val logs = ArrayList<Log>()
        val fields = arrayOf(
            COLUMN_NAME_TIME,
            COLUMN_NAME_COMMAND,
            COLUMN_NAME_INPUT_FILE,
            COLUMN_NAME_OUTPUT_FILE,
            COLUMN_NAME_SUCCESSFUL,
            COLUMN_NAME_FFMPEG_OUTPUT,
            COLUMN_NAME_INPUT_SIZE,
            COLUMN_NAME_OUTPUT_SIZE,
            COLUMN_NAME_APP_VERSION
        )
        val cursor = readableDatabase.query(TABLE_NAME, fields, null,null,null,null,"$COLUMN_NAME_TIME DESC")
        while (cursor.moveToNext()) {
            logs.add(Log(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getInt(4) != 0,
                gzipDecompress(cursor.getString(5)),
                cursor.getLong(6),
                cursor.getLong(7),
                cursor.getString(8)
            ))
        }
        cursor.close()

        return logs
    }

    fun addLog(log: Log) {
        // only save logs if enabled in settings
        if (!settings.saveLogs) return

        Timber.d("saving log to sqlite database")

        val contentValues = ContentValues()
        contentValues.put(COLUMN_NAME_TIME, log.time)
        contentValues.put(COLUMN_NAME_COMMAND, log.command)
        contentValues.put(COLUMN_NAME_INPUT_FILE, log.inputFileName)
        contentValues.put(COLUMN_NAME_OUTPUT_FILE, log.outputFileName)
        contentValues.put(COLUMN_NAME_COMMAND, log.command)
        contentValues.put(COLUMN_NAME_SUCCESSFUL, log.successful)
        contentValues.put(COLUMN_NAME_FFMPEG_OUTPUT, gzipCompress(log.ffmpeg_output))
        contentValues.put(COLUMN_NAME_INPUT_SIZE, log.inputSize)
        contentValues.put(COLUMN_NAME_OUTPUT_SIZE, log.outputSize)
        contentValues.put(COLUMN_NAME_APP_VERSION, log.appVersion)
        writableDatabase.insert(TABLE_NAME, null, contentValues)

        logCount++

        // prune old logs
        if (logCount > MAX_LOGS_TRIGGER_DELETE) {
            Timber.d("pruning logs to $MAX_LOGS in sqlite database")
            writableDatabase.execSQL("DELETE FROM $TABLE_NAME WHERE $COLUMN_NAME_ID NOT IN (" +
                    "SELECT $COLUMN_NAME_ID FROM $TABLE_NAME ORDER BY $COLUMN_NAME_TIME DESC LIMIT $MAX_LOGS)"
            )
            // reset logCount
            logCount = MAX_LOGS


        }
    }

    fun deleteLogs() {
        writableDatabase.execSQL("DELETE FROM $TABLE_NAME")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }
    companion object {
        const val MAX_LOGS = 50
        const val MAX_LOGS_TRIGGER_DELETE = MAX_LOGS+10
        const val DATABASE_NAME = "logs.db"
        const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "Log"
        private const val COLUMN_NAME_ID = "id"
        private const val COLUMN_NAME_TIME = "time"
        private const val COLUMN_NAME_COMMAND = "command"
        private const val COLUMN_NAME_INPUT_FILE = "inputFile"
        private const val COLUMN_NAME_OUTPUT_FILE = "outputFile"
        private const val COLUMN_NAME_SUCCESSFUL = "successful"
        private const val COLUMN_NAME_FFMPEG_OUTPUT = "output"
        private const val COLUMN_NAME_INPUT_SIZE = "inputSize"
        private const val COLUMN_NAME_OUTPUT_SIZE = "outputSize"
        private const val COLUMN_NAME_APP_VERSION = "appVersion"
        const val SQL_CREATE_ENTRIES = """CREATE TABLE $TABLE_NAME (
            $COLUMN_NAME_ID integer primary key autoincrement,
            $COLUMN_NAME_TIME long,
            $COLUMN_NAME_COMMAND string,
            $COLUMN_NAME_INPUT_FILE string,
            $COLUMN_NAME_OUTPUT_FILE string,
            $COLUMN_NAME_SUCCESSFUL integer,
            $COLUMN_NAME_FFMPEG_OUTPUT string,
            $COLUMN_NAME_INPUT_SIZE long,
            $COLUMN_NAME_OUTPUT_SIZE long,
            $COLUMN_NAME_APP_VERSION string
        )"""
        const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS $TABLE_NAME"
    }
}