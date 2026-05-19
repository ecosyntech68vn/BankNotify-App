package com.banknotify.webhook

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.banknotify.model.Transaction
import com.google.gson.Gson
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object WebhookManager {

    private const val PREFS_NAME = "webhook_prefs"
    private const val KEY_URL = "webhook_url"
    private const val KEY_ENABLED = "webhook_enabled"
    private const val KEY_SECRET = "webhook_secret"
    private const val KEY_RETRY_COUNT = "webhook_retry_count"
    private const val DEFAULT_RETRY = 3

    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private val TAG = "WebhookManager"

    private fun getPrefs(): SharedPreferences {
        if (prefs == null) {
            prefs = com.banknotify.BankNotifyApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    var webhookUrl: String
        get() = getPrefs().getString(KEY_URL, "") ?: ""
        set(value) = getPrefs().edit().putString(KEY_URL, value).apply()

    var isEnabled: Boolean
        get() = getPrefs().getBoolean(KEY_ENABLED, false)
        set(value) = getPrefs().edit().putBoolean(KEY_ENABLED, value).apply()

    var secret: String
        get() = getPrefs().getString(KEY_SECRET, "") ?: ""
        set(value) = getPrefs().edit().putString(KEY_SECRET, value).apply()

    var retryCount: Int
        get() = getPrefs().getInt(KEY_RETRY_COUNT, DEFAULT_RETRY)
        set(value) = getPrefs().edit().putInt(KEY_RETRY_COUNT, value).apply()

    fun dispatch(transaction: Transaction) {
        if (!isEnabled || webhookUrl.isBlank()) return

        val payload = buildPayload(transaction)

        executor.execute {
            sendWithRetry(webhookUrl, payload, retryCount)
        }
    }

    fun testWebhook(url: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val testPayload = """{"test": true, "message": "BankNotify webhook test"}"""
                OutputStreamWriter(conn.outputStream).use { it.write(testPayload) }

                val code = conn.responseCode
                val response = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $code"
                }

                handler.post {
                    if (code in 200..299) {
                        callback(true, "OK: $response")
                    } else {
                        callback(false, "HTTP $code: $response")
                    }
                }
            } catch (e: Exception) {
                handler.post { callback(false, e.message ?: "Unknown error") }
            }
        }
    }

    private fun buildPayload(tx: Transaction): String {
        val map = mapOf(
            "event" to "transaction.new",
            "id" to tx.id,
            "bank_code" to tx.bankCode,
            "bank_name" to tx.bankName,
            "account_number" to tx.accountNumber,
            "amount" to tx.amount,
            "balance" to (tx.balance ?: 0),
            "content" to tx.content,
            "sender_name" to (tx.senderName ?: ""),
            "sender_account" to (tx.senderAccount ?: ""),
            "reference_number" to (tx.referenceNumber ?: ""),
            "transaction_date" to tx.transactionDate,
            "status" to tx.status.name,
            "timestamp" to System.currentTimeMillis()
        )
        return gson.toJson(map)
    }

    private fun sendWithRetry(url: String, payload: String, maxRetries: Int) {
        var attempt = 0
        while (attempt <= maxRetries) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                if (secret.isNotBlank()) {
                    conn.setRequestProperty("X-Webhook-Secret", secret)
                }
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                OutputStreamWriter(conn.outputStream).use { it.write(payload) }

                val code = conn.responseCode
                if (code in 200..299) {
                    Log.i(TAG, "Webhook sent successfully (attempt ${attempt + 1})")
                    return
                }
                Log.w(TAG, "Webhook returned $code (attempt ${attempt + 1})")
            } catch (e: Exception) {
                Log.e(TAG, "Webhook attempt ${attempt + 1} failed: ${e.message}")
            }

            attempt++
            if (attempt <= maxRetries) {
                try {
                    Thread.sleep((attempt * 2000).toLong())
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        Log.e(TAG, "Webhook failed after $maxRetries retries")
    }
}
