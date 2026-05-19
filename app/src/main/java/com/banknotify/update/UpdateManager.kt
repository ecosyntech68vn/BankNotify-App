package com.banknotify.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.banknotify.core.BankNotifyApp
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS_KEY_CHECK_URL = "update_check_url"
    private const val PREFS_KEY_DOWNLOAD_DIR = "update_download_dir"
    private const val DEFAULT_CHECK_URL = ""
    private const val DEFAULT_DOWNLOAD_DIR = "updates"

    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()

    var checkUrl: String
        get() = BankNotifyApp.instance.getSharedPreferences(BankNotifyApp.PREF_UPDATE, Context.MODE_PRIVATE)
            .getString(PREFS_KEY_CHECK_URL, DEFAULT_CHECK_URL) ?: DEFAULT_CHECK_URL
        set(value) = BankNotifyApp.instance.getSharedPreferences(BankNotifyApp.PREF_UPDATE, Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY_CHECK_URL, value).apply()

    fun checkForUpdate(callback: (UpdateCheckResponse) -> Unit) {
        val url = checkUrl
        if (url.isBlank()) {
            callback(UpdateCheckResponse(hasUpdate = false))
            return
        }

        executor.execute {
            try {
                val app = BankNotifyApp.instance
                val request = UpdateCheckRequest(
                    currentVersion = app.appVersion,
                    currentBuild = app.appBuild,
                    packageName = app.packageName,
                    deviceInfo = mapOf(
                        "os" to "Android ${Build.VERSION.RELEASE}",
                        "sdk" to Build.VERSION.SDK_INT.toString(),
                        "model" to "${Build.MANUFACTURER} ${Build.MODEL}"
                    )
                )

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.write(gson.toJson(request).toByteArray())

                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val response = gson.fromJson(body, UpdateCheckResponse::class.java)
                    callback(response)
                } else {
                    Log.w(TAG, "Update check failed: HTTP $code")
                    callback(UpdateCheckResponse(hasUpdate = false))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check error: ${e.message}")
                callback(UpdateCheckResponse(hasUpdate = false))
            }
        }
    }

    fun downloadUpdate(updateInfo: UpdateInfo, progress: (Int) -> Unit, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val dir = File(BankNotifyApp.instance.cacheDir, DEFAULT_DOWNLOAD_DIR)
                dir.mkdirs()
                val apkFile = File(dir, "update-${updateInfo.latestVersion}.apk")

                val conn = URL(updateInfo.downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.connect()

                val totalSize = conn.contentLengthLong
                val inputStream = conn.inputStream
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        val pct = ((totalRead * 100) / totalSize).toInt()
                        progress(pct)
                    }
                }

                outputStream.close()
                inputStream.close()

                callback(true, apkFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                callback(false, e.message ?: "Download failed")
            }
        }
    }

    fun installUpdate(apkPath: String): Boolean {
        return try {
            val context = BankNotifyApp.instance
            val apkFile = File(apkPath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}")
            false
        }
    }

    fun getUpdateCheckUrl(): String = checkUrl
    fun setUpdateCheckUrl(url: String) { checkUrl = url.trim() }
}
