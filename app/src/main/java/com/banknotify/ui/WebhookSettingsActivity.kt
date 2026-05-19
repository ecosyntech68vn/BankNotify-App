package com.banknotify.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.banknotify.core.BankNotifyApp
import com.banknotify.databinding.ActivityWebhookBinding
import com.banknotify.service.webhook.WebhookManager

class WebhookSettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityWebhookBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWebhookBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Cấu hình Webhook"

        loadConfig()
        b.btnSave.setOnClickListener { save() }
        b.btnTest.setOnClickListener { test() }
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }

    private fun loadConfig() {
        b.webhookUrl.setText(WebhookManager.webhookUrl)
        b.webhookSecret.setText(WebhookManager.secret)
        b.webhookRetry.setText(WebhookManager.retryCount.toString())
        b.webhookEnabled.isChecked = WebhookManager.isEnabled
        updateStatus()
    }

    private fun save() {
        WebhookManager.webhookUrl = b.webhookUrl.text.toString().trim()
        WebhookManager.secret = b.webhookSecret.text.toString().trim()
        WebhookManager.isEnabled = b.webhookEnabled.isChecked
        WebhookManager.retryCount = b.webhookRetry.text.toString().toIntOrNull() ?: 3
        updateStatus()
        Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show()
    }

    private fun test() {
        val url = b.webhookUrl.text.toString().trim()
        if (url.isBlank()) { Toast.makeText(this, "Nhập URL", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Đang kiểm tra...", Toast.LENGTH_SHORT).show()
        WebhookManager.testWebhook(url) { success, message ->
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(if (success) "Thành công" else "Thất bại")
                    .setMessage(message)
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    private fun updateStatus() {
        b.webhookStatus.text = when {
            WebhookManager.webhookUrl.isBlank() -> "Chưa cấu hình webhook"
            WebhookManager.isEnabled -> "Webhook đang bật → ${WebhookManager.webhookUrl}"
            else -> "Webhook đang tắt"
        }
    }
}
