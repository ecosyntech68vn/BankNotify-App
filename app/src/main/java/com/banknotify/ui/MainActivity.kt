package com.banknotify.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import com.banknotify.core.AppConfig
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.biometric.BiometricLock
import com.banknotify.core.export.DataExporter
import com.banknotify.core.license.LicenseManager
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionStatus
import com.banknotify.R
import com.banknotify.databinding.ActivityMainBinding
import com.banknotify.service.listener.BankNotificationListener
import com.banknotify.service.server.ApiServerService
import com.banknotify.ui.adapter.TransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: TransactionAdapter

    @Inject
    lateinit var dbHelper: com.banknotify.core.db.DatabaseHelper

    @Inject
    lateinit var updateManager: com.banknotify.update.UpdateManager

    @Inject
    lateinit var appConfig: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!OnboardingActivity.isDone(this)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        checkLicense()
        checkBiometric()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        // Edge-to-edge (targetSdk 35): chừa padding bottom = chiều cao thanh điều hướng
        val basePaddingBottom = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(b.scrollContent) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = basePaddingBottom + bars.bottom)
            insets
        }

        adapter = TransactionAdapter { tx -> showDetail(tx) }
        b.recyclerTransactions.layoutManager = LinearLayoutManager(this)
        b.recyclerTransactions.adapter = adapter

        b.cardPermissions.setOnClickListener { openPermissionSettings() }
        b.cardWebhook.setOnClickListener { startActivity(android.content.Intent(this, WebhookSettingsActivity::class.java)) }
        b.cardStats.setOnClickListener { startActivity(android.content.Intent(this, DashboardActivity::class.java)) }
        b.cardClear.setOnClickListener { showClearDialog() }
        b.cardUpdate.setOnClickListener { showUpdateDialog() }

        setupSwitches()
        observeData()
        checkCrashes()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun checkCrashes() {
        val crashes = com.banknotify.core.crash.CrashReporter.getPendingCrashReports(this)
        if (crashes.isEmpty()) return
        val latest = crashes.first()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.crash_title))
            .setMessage(getString(R.string.crash_message))
            .setPositiveButton(getString(R.string.btn_report)) { _, _ ->
                com.banknotify.core.crash.CrashReporter.shareCrashReport(this, latest)
            }
            .setNeutralButton(getString(R.string.btn_details)) { _, _ ->
                val content = com.banknotify.core.crash.CrashReporter.getCrashContent(latest)
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.crash_detail_title))
                    .setMessage(content)
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .setNeutralButton(getString(R.string.btn_send)) { _, _ ->
                        com.banknotify.core.crash.CrashReporter.shareCrashReport(this, latest)
                    }.show()
            }
            .setNegativeButton(getString(R.string.btn_dismiss)) { _, _ ->
                com.banknotify.core.crash.CrashReporter.deleteCrashReport(latest)
            }.show()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(com.banknotify.R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            com.banknotify.R.id.action_about -> { showAbout(); true }
            com.banknotify.R.id.action_api_docs -> { showApiDocs(); true }
            com.banknotify.R.id.action_export -> { showExportDialog(); true }
            com.banknotify.R.id.action_dashboard -> { startActivity(android.content.Intent(this, DashboardActivity::class.java)); true }
            com.banknotify.R.id.action_security -> { showSecurityDialog(); true }
            com.banknotify.R.id.action_license -> { showLicenseActivationDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            val pager = Pager(PagingConfig(pageSize = 20)) {
                dbHelper.getTransactionPagingSource()
            }.flow.cachedIn(lifecycleScope)

            pager.collectLatest { pagingData ->
                adapter.submitData(lifecycle, pagingData)
            }
        }

        lifecycleScope.launch {
            combine(
                dbHelper.observeTotalCount(),
                dbHelper.observeTotalAmount(),
                dbHelper.observeUnreadCount()
            ) { count, amount, unread ->
                StatsData(count, amount, unread)
            }.collect { data ->
                b.transactionCount.text = data.totalCount.toString()
                b.totalAmount.text = getString(R.string.item_amount_format, data.totalAmount)
                b.unreadCount.text = data.unreadCount.toString()
            }
        }
    }

    private fun setupSwitches() {
        b.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!BankNotificationListener.isNotificationListenerEnabled(this)) {
                    BankNotificationListener.openNotificationListenerSettings(this)
                    b.notificationSwitch.isChecked = false
                }
            } else {
                Toast.makeText(this, getString(R.string.monitor_disabled_hint), Toast.LENGTH_LONG).show()
            }
        }
        b.serverSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(BankNotifyApp.PREF_SERVER, android.content.Context.MODE_PRIVATE)
            val port = prefs.getInt(BankNotifyApp.KEY_SERVER_PORT, BankNotifyApp.DEFAULT_PORT)
            if (isChecked) {
                ApiServerService.start(this, port)
                Toast.makeText(this, getString(R.string.server_running, port), Toast.LENGTH_SHORT).show()
            } else {
                ApiServerService.stop(this)
                Toast.makeText(this, getString(R.string.server_stopped), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatus() {
        val enabled = BankNotificationListener.isNotificationListenerEnabled(this)
        b.notificationSwitch.isChecked = enabled
        b.statusText.text = getString(if (enabled) R.string.monitoring_active else R.string.monitoring_inactive)
        b.statusText.setTextColor(
            if (enabled) androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        val prefs = getSharedPreferences(BankNotifyApp.PREF_SERVER, android.content.Context.MODE_PRIVATE)
        b.serverPortText.text = getString(R.string.server_port, prefs.getInt(BankNotifyApp.KEY_SERVER_PORT, BankNotifyApp.DEFAULT_PORT))
        b.serverSwitch.isChecked = ApiServerService.isRunning
    }

    private fun showClearDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_dialog_title))
            .setMessage(getString(R.string.clear_dialog_message))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                dbHelper.deleteAllTransactions()
                Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private fun showDetail(tx: Transaction) {
        val na = getString(R.string.tx_na)
        val detail = buildString {
            appendLine("${getString(R.string.item_bank)}: ${tx.bankName} (${tx.bankCode})")
            appendLine(getString(R.string.tx_amount_label, getString(R.string.item_amount_format, tx.amount)))
            appendLine(getString(R.string.tx_sender_label, tx.senderName ?: na))
            appendLine("${getString(R.string.item_content)}: ${tx.content}")
            appendLine(getString(R.string.tx_ref_label, tx.referenceNumber ?: na))
            appendLine(getString(R.string.tx_balance_label, tx.balance?.let { getString(R.string.item_amount_format, it) } ?: na))
            appendLine(getString(R.string.tx_time_label,
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(tx.transactionDate))))
            appendLine("${getString(R.string.stats_transactions)}: ${tx.status.name}")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tx_detail_title)).setMessage(detail)
            .setPositiveButton(getString(R.string.btn_ok), null)
            .setNeutralButton(getString(R.string.tx_confirm)) { _, _ ->
                dbHelper.updateStatus(tx.id, TransactionStatus.CONFIRMED)
                Toast.makeText(this, getString(R.string.tx_confirmed), Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showUpdateDialog() {
        val url = updateManager.checkUrl
        val input = android.widget.EditText(this).apply { setText(url); hint = getString(R.string.update_url_hint) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_dialog_title))
            .setMessage(getString(R.string.update_dialog_message))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                updateManager.checkUrl = input.text.toString()
                Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage(getString(R.string.about_message, appConfig.version))
            .setPositiveButton(getString(R.string.btn_ok), null).show()
    }

    private fun showApiDocs() {
        val port = getSharedPreferences(BankNotifyApp.PREF_SERVER, android.content.Context.MODE_PRIVATE)
            .getInt(BankNotifyApp.KEY_SERVER_PORT, BankNotifyApp.DEFAULT_PORT)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.api_docs_title))
            .setMessage(getString(R.string.api_docs_message, port))
            .setPositiveButton(getString(R.string.btn_ok), null).show()
    }

    private fun openPermissionSettings() {
        startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun checkBiometric() {
        if (!BiometricLock.isEnabled(this)) return
        BiometricLock.authenticate(this,
            getString(R.string.biometric_title),
            getString(R.string.biometric_subtitle)) { }
    }

    private fun showSecurityDialog() {
        val enabled = BiometricLock.isEnabled(this)
        if (!BiometricLock.isSupported(this)) {
            Toast.makeText(this, getString(R.string.biometric_not_supported), Toast.LENGTH_SHORT).show()
            return
        }
        if (enabled) {
            BiometricLock.setEnabled(this, false)
            Toast.makeText(this, getString(R.string.biometric_disabled), Toast.LENGTH_SHORT).show()
        } else {
            BiometricLock.authenticate(this,
                getString(R.string.biometric_enable_title),
                getString(R.string.biometric_enable_subtitle)) {
                BiometricLock.setEnabled(this, true)
                Toast.makeText(this, getString(R.string.biometric_enabled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportDialog() {
        val items = arrayOf("JSON", "CSV")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_export))
            .setItems(items) { _, which ->
                val file = if (which == 0) DataExporter.exportJson(this, dbHelper) else DataExporter.exportCsv(this, dbHelper)
                val mime = if (which == 0) "application/json" else "text/csv"
                DataExporter.shareFile(this, file, mime)
            }.show()
    }

    private fun checkLicense() {
        if (LicenseManager.isLicensed(this)) return
        LicenseManager.startTrial(this)
        if (LicenseManager.isTrialExpired(this)) {
            showLicenseExpiredDialog()
        } else {
            showTrialBanner()
        }
    }

    private fun showTrialBanner() {
        val days = LicenseManager.getTrialDaysLeft(this)
        com.google.android.material.snackbar.Snackbar.make(b.root,
            getString(R.string.license_trial_days, days),
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
            .setAction(getString(R.string.menu_license)) { showLicenseActivationDialog() }
            .show()
    }

    private fun showLicenseExpiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.license_expired_title))
            .setMessage(getString(R.string.license_expired_message))
            .setPositiveButton(getString(R.string.menu_license)) { _, _ -> showLicenseActivationDialog() }
            .setNegativeButton(getString(R.string.btn_dismiss)) { _, _ -> finish() }
            .setCancelable(false).show()
    }

    private fun showLicenseActivationDialog() {
        val isLicensed = LicenseManager.isLicensed(this)
        val email = LicenseManager.getLicensedEmail(this)

        if (isLicensed) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.license_activated_title))
                .setMessage(getString(R.string.license_activated_message, email))
                .setPositiveButton(getString(R.string.btn_ok), null)
                .setNeutralButton(getString(R.string.btn_deactivate)) { _, _ ->
                    LicenseManager.deactivate(this)
                    Toast.makeText(this, getString(R.string.license_deactivated), Toast.LENGTH_SHORT).show()
                }.show()
            return
        }

        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.license_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_license))
            .setMessage(getString(R.string.license_prompt))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_activate)) { _, _ ->
                val key = input.text.toString().trim()
                if (key.isBlank()) return@setPositiveButton
                val result = LicenseManager.activate(this, key)
                val msg = when (result) {
                    is LicenseManager.LicenseResult.OK -> getString(R.string.license_success)
                    is LicenseManager.LicenseResult.INVALID_FORMAT -> getString(R.string.license_error_format)
                    is LicenseManager.LicenseResult.INVALID_SIGNATURE -> getString(R.string.license_error_signature)
                    is LicenseManager.LicenseResult.EXPIRED -> getString(R.string.license_error_expired)
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private data class StatsData(
        val totalCount: Int,
        val totalAmount: Double,
        val unreadCount: Int
    )
}
