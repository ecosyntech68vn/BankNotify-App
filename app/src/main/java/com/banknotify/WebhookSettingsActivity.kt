package com.banknotify

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.banknotify.webhook.WebhookManager

class WebhookSettingsActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var secretInput: EditText
    private lateinit var retryInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webhook)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Cấu hình Webhook"

        urlInput = findViewById(R.id.webhook_url)
        secretInput = findViewById(R.id.webhook_secret)
        retryInput = findViewById(R.id.webhook_retry)
        enabledSwitch = findViewById(R.id.webhook_enabled)
        statusText = findViewById(R.id.webhook_status)

        loadConfig()

        findViewById<Button>(R.id.btn_save).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.btn_test).setOnClickListener { testWebhook() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadConfig() {
        urlInput.setText(WebhookManager.webhookUrl)
        secretInput.setText(WebhookManager.secret)
        retryInput.setText(WebhookManager.retryCount.toString())
        enabledSwitch.isChecked = WebhookManager.isEnabled
        updateStatus()
    }

    private fun saveConfig() {
        WebhookManager.webhookUrl = urlInput.text.toString().trim()
        WebhookManager.secret = secretInput.text.toString().trim()
        WebhookManager.isEnabled = enabledSwitch.isChecked
        WebhookManager.retryCount = retryInput.text.toString().toIntOrNull() ?: 3

        updateStatus()
        Toast.makeText(this, "Đã lưu cấu hình webhook", Toast.LENGTH_SHORT).show()
    }

    private fun testWebhook() {
        val url = urlInput.text.toString().trim()
        if (url.isBlank()) {
            Toast.makeText(this, "Nhập URL webhook", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Đang kiểm tra webhook...", Toast.LENGTH_SHORT).show()
        WebhookManager.testWebhook(url) { success, message ->
            runOnUiThread {
                val title = if (success) "Thành công" else "Thất bại"
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun updateStatus() {
        val url = WebhookManager.webhookUrl
        val enabled = WebhookManager.isEnabled
        statusText.text = when {
            url.isBlank() -> "Chưa cấu hình webhook"
            enabled -> "Webhook đang bật → $url"
            else -> "Webhook đang tắt"
        }
    }
}
