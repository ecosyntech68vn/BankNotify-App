package com.banknotify.listener

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.banknotify.BankNotifyApp
import com.banknotify.model.TransactionStatus
import com.banknotify.parser.BankParserRegistry
import com.banknotify.webhook.WebhookManager

@SuppressLint("OverrideAbstract")
class BankNotificationListener : NotificationListenerService() {

    private val TAG = "BankNotify"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE, "")
        val body = notification.extras.getCharSequence(Notification.EXTRA_TEXT, "")?.toString() ?: ""
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "")?.toString() ?: ""

        val fullBody = if (bigText.isNotEmpty()) "$body $bigText" else body

        Log.d(TAG, "Notification from: $packageName | Title: $title | Body: $fullBody")

        if (packageName.contains("bank", ignoreCase = true) ||
            packageName.contains("vietcombank", ignoreCase = true) ||
            packageName.contains("techcombank", ignoreCase = true) ||
            BankParserRegistry.getParserForPackage(packageName) != null) {

            val transaction = BankParserRegistry.parse(packageName, title, fullBody)

            if (transaction != null) {
                val db = BankNotifyApp.instance.dbHelper
                val id = db.insertTransaction(transaction)
                val savedTx = transaction.copy(id = id)

                Log.i(TAG, "Parsed transaction: ${savedTx.bankName} | ${savedTx.amount} VND | ${savedTx.content}")

                showTransactionNotification(savedTx)
                WebhookManager.dispatch(savedTx)
            } else {
                Log.w(TAG, "Failed to parse notification from $packageName")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }

    private fun showTransactionNotification(tx: com.banknotify.model.Transaction) {
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

    fun isListening(): Boolean {
        val listeners = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                this.activeNotifications
                true
            } else false
        } catch (_: Exception) {
            false
        }
        return listeners
    }

    companion object {
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(packageName) == true
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
