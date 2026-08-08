package com.vineyard.hfm.app;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppLogger {

    private static final String TAG = "AppLogger";
    private static final String LOG_DIR_NAME = "hfm log report";
    private static final String LOG_FILE_NAME = "hfm_diagnostic_log.txt";
    private static final Object LOCK = new Object();

    private static File getLogFile() {
        File externalStorage = Environment.getExternalStorageDirectory();
        File logDir = new File(externalStorage, LOG_DIR_NAME);
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create log directory at: " + logDir.getAbsolutePath());
            }
        }
        return new File(logDir, LOG_FILE_NAME);
    }

    public static void log(String tag, String message) {
        log(tag, message, null);
    }

    public static void logError(String tag, String message, Throwable throwable) {
        log(tag, "ERROR | " + message, throwable);
    }

    public static void logMetric(String tag, String operation, long durationMs, String details) {
        String metricMessage = String.format(Locale.US, "[METRIC] %s executed in %d ms | %s", operation, durationMs, details != null ? details : "");
        log(tag, metricMessage, null);
    }

    public static void log(String tag, String message, Throwable throwable) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append(timestamp)
                .append(" [")
                .append(Thread.currentThread().getName())
                .append("] ")
                .append(tag)
                .append(": ")
                .append(message);

        if (throwable != null) {
            logBuilder.append("\n");
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            logBuilder.append(stringWriter.toString());
        }
        logBuilder.append("\n");

        String formattedLog = logBuilder.toString();
        Log.d(tag, message, throwable);

        synchronized (LOCK) {
            FileWriter writer = null;
            try {
                File logFile = getLogFile();
                writer = new FileWriter(logFile, true);
                writer.write(formattedLog);
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error writing entry to diagnostic log file", e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
} 