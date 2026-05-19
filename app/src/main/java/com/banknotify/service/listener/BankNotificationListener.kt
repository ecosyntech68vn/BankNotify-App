package com.banknotify.service.listener

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.model.Transaction
import com.banknotify.parser.BankParserRegistry
import com.banknotify.service.webhook.WebhookManager

@SuppressLint("OverrideAbstract")
class BankNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras.getString(android.app.Notification.EXTRA_TITLE, "")
        val body = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT, "")?.toString() ?: ""
        val bigText = notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT, "")?.toString() ?: ""

        val fullBody = if (bigText.isNotEmpty()) "$body $bigText" else body

        Log.d(TAG, "Notification from: $packageName")

        if (BankParserRegistry.getParserForPackage(packageName) != null ||
            packageName.contains("bank", ignoreCase = true)) {

            val transaction = BankParserRegistry.parse(packageName, title, fullBody)

            if (transaction != null) {
                val db = BankNotifyApp.instance.dbHelper
                val id = db.insertTransaction(transaction)
                val savedTx = transaction.copy(id = id)
                Log.i(TAG, "Parsed: ${savedTx.bankName} | ${savedTx.amount} VND")
                notifyUser(savedTx)
                WebhookManager.dispatch(savedTx)
            } else {
                Log.w(TAG, "Unparsed notification from $packageName")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
    }

    private fun notifyUser(tx: Transaction) {
        val nm = getSystemService(NotificationManager::class.java)
        val text = "${tx.bankName}: +${String.format("%,.0f", tx.amount)} VND\n${tx.content}"
        val notification = NotificationCompat.Builder(this, BankNotifyApp.CHANNEL_TRANSACTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Giao dịch mới - ${tx.bankName}")
            .setContentText("+${String.format("%,.0f", tx.amount)} VND")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(tx.id.toInt(), notification)
    }

    companion object {
        private const val TAG = "BankNotify"

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }

        fun openNotificationListenerSettings(context: Context) {
            context.startActivity(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
