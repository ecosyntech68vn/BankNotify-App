package com.banknotify.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.banknotify.R
import com.banknotify.databinding.ActivityWebhookBinding
import com.banknotify.service.webhook.WebhookManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WebhookSettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityWebhookBinding

    @Inject lateinit var webhookManager: WebhookManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityWebhookBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.webhook_config_title)

        loadConfig()
        b.btnSave.setOnClickListener { save() }
        b.btnTest.setOnClickListener { test() }
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }

    private fun loadConfig() {
        b.webhookUrl.setText(webhookManager.webhookUrl)
        b.webhookSecret.setText(webhookManager.secret)
        b.webhookRetry.setText(webhookManager.retryCount.toString())
        b.webhookEnabled.isChecked = webhookManager.isEnabled
        updateStatus()
    }

    private fun save() {
        webhookManager.webhookUrl = b.webhookUrl.text.toString().trim()
        webhookManager.secret = b.webhookSecret.text.toString().trim()
        webhookManager.isEnabled = b.webhookEnabled.isChecked
        webhookManager.retryCount = b.webhookRetry.text.toString().toIntOrNull() ?: 3
        updateStatus()
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }

    private fun test() {
        val url = b.webhookUrl.text.toString().trim()
        if (url.isBlank()) { Toast.makeText(this, getString(R.string.webhook_url_required), Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, getString(R.string.webhook_testing), Toast.LENGTH_SHORT).show()
        webhookManager.testWebhook(url) { success, message ->
            AlertDialog.Builder(this)
                .setTitle(getString(if (success) R.string.webhook_test_success else R.string.webhook_test_fail))
                .setMessage(message)
                .setPositiveButton(getString(R.string.btn_ok), null).show()
        }
    }

    private fun updateStatus() {
        b.webhookStatus.text = when {
            webhookManager.webhookUrl.isBlank() -> getString(R.string.webhook_url_empty)
            webhookManager.isEnabled -> getString(R.string.webhook_enabled_msg, webhookManager.webhookUrl)
            else -> getString(R.string.webhook_disabled_msg)
        }
    }
}
