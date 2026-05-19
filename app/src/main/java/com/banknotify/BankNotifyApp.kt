package com.banknotify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.banknotify.db.DatabaseHelper

class BankNotifyApp : Application() {

    lateinit var dbHelper: DatabaseHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        dbHelper = DatabaseHelper(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val listenerChannel = NotificationChannel(
                CHANNEL_LISTENER,
                "Dịch vụ giám sát",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo cho dịch vụ giám sát ngân hàng"
            }

            val serverChannel = NotificationChannel(
                CHANNEL_SERVER,
                "Máy chủ API",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo cho máy chủ API local"
            }

            val transactionChannel = NotificationChannel(
                CHANNEL_TRANSACTION,
                "Giao dịch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi có giao dịch mới"
            }

            nm.createNotificationChannels(
                listOf(listenerChannel, serverChannel, transactionChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_LISTENER = "banknotify_listener"
        const val CHANNEL_SERVER = "banknotify_server"
        const val CHANNEL_TRANSACTION = "banknotify_transaction"

        lateinit var instance: BankNotifyApp
            private set
    }
}
