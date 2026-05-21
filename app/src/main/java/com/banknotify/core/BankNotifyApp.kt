package com.banknotify.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.banknotify.core.crash.CrashReporter
import com.banknotify.core.db.DatabaseHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BankNotifyApp : Application() {

    lateinit var dbHelper: DatabaseHelper
        private set

    var appVersion: String = "1.0.0"
    var appBuild: Int = 1

    override fun onCreate() {
        super.onCreate()
        instance = this
        dbHelper = DatabaseHelper(this)
        appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
        appBuild = try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (_: Exception) { 1 }
        CrashReporter.init(this, appVersion, appBuild)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val listenerChannel = NotificationChannel(
                CHANNEL_LISTENER, "Dịch vụ giám sát",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Thông báo cho dịch vụ giám sát ngân hàng" }

            val serverChannel = NotificationChannel(
                CHANNEL_SERVER, "Máy chủ API",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Thông báo cho máy chủ API local" }

            val transactionChannel = NotificationChannel(
                CHANNEL_TRANSACTION, "Giao dịch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Thông báo khi có giao dịch mới" }

            nm.createNotificationChannels(listOf(listenerChannel, serverChannel, transactionChannel))
        }
    }

    companion object {
        const val CHANNEL_LISTENER = "banknotify_listener"
        const val CHANNEL_SERVER = "banknotify_server"
        const val CHANNEL_TRANSACTION = "banknotify_transaction"
        const val PREF_SERVER = "server_prefs"
        const val PREF_UPDATE = "update_prefs"
        const val PREF_WEBHOOK = "webhook_prefs"
        const val DEFAULT_PORT = 8765
        const val KEY_SERVER_PORT = "server_port"

        lateinit var instance: BankNotifyApp
            private set
    }
}
