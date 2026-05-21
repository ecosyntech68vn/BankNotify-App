package com.banknotify.core.crash

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val CRASH_DIR = "crashes"
    private var initialized = false
    private var appVersion = ""
    private var appBuild = 0

    fun init(context: Context, version: String, build: Int) {
        if (initialized) return
        initialized = true
        appVersion = version
        appBuild = build
        getCrashDir(context).mkdirs()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrash(context, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getPendingCrashReports(context: Context): List<File> {
        val dir = getCrashDir(context)
        return dir.listFiles { f -> f.name.endsWith(".stacktrace") }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getCrashContent(file: File): String = file.readText()

    fun deleteCrashReport(file: File) = file.delete()

    fun shareCrashReport(context: Context, file: File) {
        val content = getCrashContent(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "BankNotify Crash Report")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Gửi báo cáo lỗi"))
    }

    private fun saveCrash(context: Context, throwable: Throwable) {
        try {
            val dir = getCrashDir(context)
            dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "crash_$timestamp.stacktrace")
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("=== BankNotify Crash Report ===")
            pw.println("Time: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date())}")
            pw.println("Version: $appVersion (build $appBuild)")
            pw.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            pw.println("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            pw.println()
            throwable.printStackTrace(pw)
            pw.flush()
            FileWriter(file).use { it.write(sanitize(sw.toString())) }
            Log.e(TAG, "Crash saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash", e)
        }
    }

    private fun getCrashDir(context: Context): File =
        File(context.cacheDir, CRASH_DIR)

    private val REDACT_PATTERNS = listOf(
        Regex("""(?i)"(secret|api_key|webhook_secret|password|token)"\s*:\s*"[^"]*"""),
        Regex("""(?i)(x-api-key|authorization)\s*[:=]\s*[^\s,;}]+"""),
        Regex("""\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b"""),
    )

    private fun sanitize(text: String): String {
        var result = text
        for (pattern in REDACT_PATTERNS) {
            result = pattern.replace(result) { match ->
                val s = match.value
                val idx = s.indexOfAny(charArrayOf(':', '='))
                if (idx >= 0) s.substring(0, idx + 1) + " [REDACTED]"
                else "[REDACTED]"
            }
        }
        return result
    }
}
