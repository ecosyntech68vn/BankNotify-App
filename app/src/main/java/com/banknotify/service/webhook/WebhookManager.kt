package com.banknotify.service.webhook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.model.Transaction
import com.google.gson.Gson
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object WebhookManager {

    private const val KEY_URL = "webhook_url"
    private const val KEY_ENABLED = "webhook_enabled"
    private const val KEY_SECRET = "webhook_secret"
    private const val KEY_RETRY = "webhook_retry_count"
    private const val DEFAULT_RETRY = 3

    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "WebhookManager"

    private fun prefs() = BankNotifyApp.instance.getSharedPreferences(BankNotifyApp.PREF_WEBHOOK, Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs().getString(KEY_URL, "") ?: ""
        set(value) = prefs().edit().putString(KEY_URL, value.trim()).apply()

    var isEnabled: Boolean
        get() = prefs().getBoolean(KEY_ENABLED, false)
        set(value) = prefs().edit().putBoolean(KEY_ENABLED, value).apply()

    var secret: String
        get() = prefs().getString(KEY_SECRET, "") ?: ""
        set(value) = prefs().edit().putString(KEY_SECRET, value.trim()).apply()

    var retryCount: Int
        get() = prefs().getInt(KEY_RETRY, DEFAULT_RETRY)
        set(value) = prefs().edit().putInt(KEY_RETRY, value.coerceIn(0, 10)).apply()

    fun dispatch(transaction: Transaction) {
        if (!isEnabled || webhookUrl.isBlank()) return
        if (!isValidUrl(webhookUrl)) {
            Log.w(TAG, "Invalid webhook URL: $webhookUrl")
            return
        }
        val payload = buildPayload(transaction)
        executor.execute { sendWithRetry(webhookUrl, payload, retryCount) }
    }

    fun testWebhook(url: String, callback: (Boolean, String) -> Unit) {
        if (!isValidUrl(url)) {
            handler.post { callback(false, "Invalid URL. Must be http:// or https://") }
            return
        }
        executor.execute {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                OutputStreamWriter(conn.outputStream).use { it.write("""{"test":true,"message":"BankNotify webhook test"}""") }
                val code = conn.responseCode
                val body = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
                    else conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                handler.post {
                    if (code in 200..299) callback(true, body)
                    else callback(false, body)
                }
            } catch (e: Exception) {
                handler.post { callback(false, e.message ?: "Unknown error") }
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun buildPayload(tx: Transaction): String {
        return gson.toJson(mapOf(
            "event" to "transaction.new",
            "id" to tx.id,
            "bank_code" to tx.bankCode,
            "bank_name" to tx.bankName,
            "account_number" to tx.accountNumber,
            "amount" to tx.amount,
            "balance" to tx.balance,
            "content" to tx.content,
            "sender_name" to (tx.senderName ?: ""),
            "sender_account" to (tx.senderAccount ?: ""),
            "reference_number" to (tx.referenceNumber ?: ""),
            "transaction_date" to tx.transactionDate,
            "status" to tx.status.name,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    private fun sendWithRetry(url: String, payload: String, maxRetries: Int) {
        var attempt = 0
        while (attempt <= maxRetries) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                if (secret.isNotBlank()) conn.setRequestProperty("X-Webhook-Secret", secret)
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }

                val code = conn.responseCode
                if (code in 200..299) {
                    Log.i(TAG, "Webhook sent (attempt ${attempt + 1})")
                    return
                }
                Log.w(TAG, "Webhook HTTP $code (attempt ${attempt + 1})")
            } catch (e: Exception) {
                Log.e(TAG, "Webhook attempt ${attempt + 1}: ${e.message}")
            }
            attempt++
            if (attempt <= maxRetries) {
                try { Thread.sleep((attempt * 2000).toLong()) } catch (_: InterruptedException) { break }
            }
        }
        Log.e(TAG, "Webhook failed after $maxRetries retries")
    }
}
