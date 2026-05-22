package com.banknotify.service.listener

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.db.DatabaseHelper
import com.banknotify.core.model.Transaction
import com.banknotify.parser.BankParserRegistry
import com.banknotify.service.webhook.WebhookManager

class BankAccessibilityService : AccessibilityService() {

    private val dbHelper: DatabaseHelper get() = BankNotifyApp.instance.dbHelper
    private val webhookManager = WebhookManager()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val parcelable = event.parcelableData
        if (parcelable !is android.app.Notification) return
        val notification = parcelable
        val title = notification.extras.getString(android.app.Notification.EXTRA_TITLE, "")
        val body = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT, "")?.toString() ?: ""
        val bigText = notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT, "")?.toString() ?: ""
        val fullBody = if (bigText.isNotEmpty()) "$body $bigText" else body

        Log.d(TAG, "Accessibility notification from: $packageName")

        if (BankParserRegistry.getParserForPackage(packageName) != null ||
            packageName.contains("bank", ignoreCase = true)) {

            val transaction = BankParserRegistry.parse(packageName, title, fullBody)
            if (transaction != null) {
                val id = dbHelper.insertTransaction(transaction)
                val savedTx = transaction.copy(id = id)
                Log.i(TAG, "Parsed: ${savedTx.bankName} | ${savedTx.amount} VND")
                notifyUser(savedTx)
                webhookManager.dispatch(savedTx)
            } else {
                Log.w(TAG, "Unparsed notification from $packageName")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
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

        fun isAccessibilityServiceEnabled(): Boolean {
            val service = "${BankNotifyApp.instance.packageName}/.service.listener.BankAccessibilityService"
            val enabledServices = android.provider.Settings.Secure.getString(
                BankNotifyApp.instance.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(service) == true
        }

        fun openAccessibilitySettings() {
            BankNotifyApp.instance.startActivity(
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
