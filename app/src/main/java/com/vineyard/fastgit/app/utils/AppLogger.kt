package com.vineyard.fastgit.app.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class LogEntry(
    val id: Long = AppLogger.nextLogId(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val tag: String,
    val message: String,
    val isError: Boolean = false,
    val isSuccess: Boolean = false
)

object AppLogger {
    private val logSequence = AtomicLong(1L)

    fun nextLogId(): Long = logSequence.getAndIncrement()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _isMinimized = MutableStateFlow(false)
    val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

    private var appContext: Context? = null
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    init {
        i("AppLogger", "FastGit Live Process & Error Logger initialized successfully.")
    }

    /**
     * Initializes the Global Crash Handler and creates the public 'fastgit log' folder in Downloads, Documents & SDCARD.
     */
    fun initCrashHandler(context: Context) {
        appContext = context.applicationContext
        
        // Ensure public SDCARD 'fastgit log' directories exist in Download, Documents, and Root SDCARD
        val createdDirs = getPublicSdcardLogDirs()
        val primaryPath = createdDirs.firstOrNull()?.absolutePath ?: "/storage/emulated/0/Download/fastgit log"

        i("AppLogger", "Public 'fastgit log' folder initialized!")
        i("AppLogger", "Log Folder Location: $primaryPath (Check Download or Documents folder in File Manager)")

        // Save an initialization session log file to public 'fastgit log' folders
        saveLogToPublicSdcard("FastGit Session Started at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\nLog Directory: $primaryPath")

        if (defaultUncaughtExceptionHandler == null) {
            defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                handleCrash(thread, throwable)
            }
            i("AppLogger", "Global Uncaught Exception Crash Handler registered.")
        }
    }

    /**
     * Obtains all accessible public log directory locations: Download/fastgit log, Documents/fastgit log, /sdcard/fastgit log
     */
    fun getPublicSdcardLogDirs(): List<File> {
        val candidates = mutableListOf<File>()

        // 1. Download folder (Most visible in standard file managers)
        try {
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fastgit log")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            if (downloadDir.exists()) candidates.add(downloadDir)
        } catch (e: Exception) {
            Log.e("AppLogger", "Error creating Download/fastgit log: ${e.message}")
        }

        // 2. Documents folder
        try {
            val docDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "fastgit log")
            if (!docDir.exists()) docDir.mkdirs()
            if (docDir.exists()) candidates.add(docDir)
        } catch (e: Exception) {
            Log.e("AppLogger", "Error creating Documents/fastgit log: ${e.message}")
        }

        // 3. Direct SDCard Root / Emulated storage
        try {
            val sdcardDir = File("/sdcard/fastgit log")
            if (!sdcardDir.exists()) sdcardDir.mkdirs()
            if (sdcardDir.exists()) candidates.add(sdcardDir)
        } catch (e: Exception) {
            Log.e("AppLogger", "Error creating /sdcard/fastgit log: ${e.message}")
        }

        // 4. Fallback App External Files Dir
        appContext?.let { ctx ->
            try {
                val appExternalDir = File(ctx.getExternalFilesDir(null), "fastgit log")
                if (!appExternalDir.exists()) appExternalDir.mkdirs()
                if (appExternalDir.exists()) candidates.add(appExternalDir)
            } catch (e: Exception) {
                Log.e("AppLogger", "Error creating App External files dir: ${e.message}")
            }
        }

        return candidates.ifEmpty {
            val fallback = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fastgit log")
            fallback.mkdirs()
            listOf(fallback)
        }
    }

    fun i(tag: String, message: String) {
        addLog(LogEntry(tag = tag, message = message, isError = false, isSuccess = false))
    }

    fun s(tag: String, message: String) {
        addLog(LogEntry(tag = tag, message = message, isError = false, isSuccess = true))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$message | Exception: ${throwable.localizedMessage ?: throwable.toString()}\nStackTrace:\n$sw"
        } else {
            message
        }
        addLog(LogEntry(tag = tag, message = fullMessage, isError = true, isSuccess = false))

        // Save error report immediately to public SDCARD 'fastgit log' directory
        saveErrorReportToPublicSdcard(tag, fullMessage, throwable)
    }

    private fun addLog(entry: LogEntry) {
        val currentList = _logs.value.toMutableList()
        currentList.add(entry)
        if (currentList.size > 1000) {
            currentList.removeAt(0)
        }
        _logs.value = currentList
    }

    private fun handleCrash(thread: Thread, throwable: Throwable) {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "fastgit_crash_$dateStr.txt"

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val crashReport = StringBuilder().apply {
            append("====================================================\n")
            append("        FASTGIT CRASH REPORT & ERROR LOG          \n")
            append("====================================================\n")
            append("Timestamp: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())).append("\n")
            append("Thread: ").append(thread.name).append(" (ID: ").append(thread.id).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("Android OS: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("----------------------------------------------------\n")
            append("EXCEPTION DETAILS:\n")
            append(throwable.javaClass.name).append(": ").append(throwable.localizedMessage ?: "No message").append("\n\n")
            append("STACK TRACE:\n")
            append(stackTrace).append("\n")
            append("----------------------------------------------------\n")
            append("RECENT APPLICATION LOGS:\n")
            append(getFullLogText()).append("\n")
            append("====================================================\n")
        }.toString()

        // 1. Write crash file to public SDCARD 'fastgit log'
        saveCrashFileToPublicSdcard(fileName, crashReport)

        // 2. Also append to 'fastgit_all_crashes.log' in the same folder
        saveCrashFileToPublicSdcard("fastgit_all_crashes.log", "\n\n$crashReport", append = true)

        Log.e("FastGitCrash", "CRASH DETECTED AND SAVED TO PUBLIC SDCARD 'fastgit log/$fileName'", throwable)

        // Forward to default system exception handler
        defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
    }

    fun exportLogsToFiles(): List<String> {
        val exportedPaths = mutableListOf<String>()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "fastgit_logs_export_$dateStr.txt"
        val fullContent = StringBuilder().apply {
            append("====================================================\n")
            append("        FASTGIT EXPORTED APP LOG REPORT            \n")
            append("====================================================\n")
            append("Export Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
            append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("----------------------------------------------------\n\n")
            append(getFullLogText()).append("\n")
        }.toString()

        for (dir in getPublicSdcardLogDirs()) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file, false).use { fos ->
                    fos.write(fullContent.toByteArray(Charsets.UTF_8))
                }
                exportedPaths.add(file.absolutePath)
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to export logs to ${dir.absolutePath}: ${e.message}")
            }
        }
        return exportedPaths
    }

    private fun saveCrashFileToPublicSdcard(fileName: String, content: String, append: Boolean = false) {
        for (dir in getPublicSdcardLogDirs()) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file, append).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                }
                Log.i("AppLogger", "Crash report written to: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to write crash report file to ${dir.absolutePath}: ${e.message}")
            }
        }
    }

    private fun saveErrorReportToPublicSdcard(tag: String, message: String, throwable: Throwable?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entryText = "[$timestamp] [ERROR] [$tag] $message\n"
        for (dir in getPublicSdcardLogDirs()) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "fastgit_error_reports.log")
                FileOutputStream(file, true).use { fos ->
                    fos.write(entryText.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to append error log to ${dir.absolutePath}: ${e.message}")
            }
        }
    }

    private fun saveLogToPublicSdcard(content: String) {
        for (dir in getPublicSdcardLogDirs()) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "fastgit_app_events.log")
                FileOutputStream(file, true).use { fos ->
                    fos.write("$content\n".toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to save log to ${dir.absolutePath}: ${e.message}")
            }
        }
    }

    fun showOverlay() {
        _isOverlayVisible.value = true
        _isMinimized.value = false
    }

    fun minimizeOverlay() {
        _isMinimized.value = true
        _isOverlayVisible.value = false
    }

    fun expandOverlay() {
        _isMinimized.value = false
        _isOverlayVisible.value = true
    }

    fun closeOverlay() {
        _isOverlayVisible.value = false
        _isMinimized.value = false
    }

    fun clearLogs() {
        _logs.value = emptyList()
        i("AppLogger", "Logs cleared.")
    }

    fun getFullLogText(): String {
        return _logs.value.joinToString("\n") { entry ->
            val status = when {
                entry.isError -> "[ERROR]"
                entry.isSuccess -> "[SUCCESS]"
                else -> "[INFO]"
            }
            "[${entry.timestamp}] $status [${entry.tag}] ${entry.message}"
        }
    }
}

