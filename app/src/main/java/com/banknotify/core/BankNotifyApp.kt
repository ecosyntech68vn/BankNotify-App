package com.banknotify.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.banknotify.R
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
                CHANNEL_LISTENER, getString(R.string.channel_listener_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_listener_desc) }

            val serverChannel = NotificationChannel(
                CHANNEL_SERVER, getString(R.string.channel_server_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_server_desc) }

            val transactionChannel = NotificationChannel(
                CHANNEL_TRANSACTION, getString(R.string.channel_transaction_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.channel_transaction_desc) }

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
