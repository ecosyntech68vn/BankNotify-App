package com.banknotify.service.webhook

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.SecurePrefs
import com.banknotify.core.model.Transaction
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookManager {

    private const val KEY_URL = "webhook_url"
    private const val KEY_ENABLED = "webhook_enabled"
    private const val KEY_SECRET = "webhook_secret"
    private const val KEY_RETRY = "webhook_retry_count"
    private const val DEFAULT_RETRY = 3

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "WebhookManager"

    private val prefs: SharedPreferences by lazy {
        BankNotifyApp.instance.getSharedPreferences(BankNotifyApp.PREF_WEBHOOK, Context.MODE_PRIVATE)
    }

    var webhookUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var secret: String
        get() = SecurePrefs.getString(BankNotifyApp.instance, BankNotifyApp.PREF_WEBHOOK, KEY_SECRET)
        set(value) = SecurePrefs.setString(BankNotifyApp.instance, BankNotifyApp.PREF_WEBHOOK, KEY_SECRET, value)

    var retryCount: Int
        get() = prefs.getInt(KEY_RETRY, DEFAULT_RETRY)
        set(value) = prefs.edit().putInt(KEY_RETRY, value.coerceIn(0, 10)).apply()

    fun dispatch(transaction: Transaction) {
        if (!isEnabled || webhookUrl.isBlank()) return
        if (!isValidUrl(webhookUrl)) {
            Log.w(TAG, "Invalid webhook URL: $webhookUrl")
            return
        }
        val payload = buildPayload(transaction)
        scope.launch { sendWithRetry(webhookUrl, payload, retryCount) }
    }

    fun testWebhook(url: String, callback: (Boolean, String) -> Unit) {
        if (!isValidUrl(url)) {
            handler.post { callback(false, "Invalid URL. Must be http:// or https://") }
            return
        }
        scope.launch {
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
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.startsWith("http://") && secret.isNotBlank()) {
            Log.w(TAG, "Blocking webhook to $url: secret would be sent over unencrypted HTTP")
            return false
        }
        return true
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
                if (secret.isNotBlank()) {
                    conn.setRequestProperty("X-Webhook-Secret", secret)
                    conn.setRequestProperty("X-Webhook-Signature", signPayload(payload))
                }
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

    private fun signPayload(payload: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            bytesToHex(mac.doFinal(payload.toByteArray()))
        } catch (e: Exception) {
            Log.e(TAG, "HMAC signing failed", e)
            ""
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}
