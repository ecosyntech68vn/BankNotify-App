package com.banknotify.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import com.banknotify.core.AppConfig
import com.banknotify.core.BankNotifyApp
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        adapter = TransactionAdapter { tx -> showDetail(tx) }
        b.recyclerTransactions.layoutManager = LinearLayoutManager(this)
        b.recyclerTransactions.adapter = adapter

        b.cardPermissions.setOnClickListener { openPermissionSettings() }
        b.cardWebhook.setOnClickListener { startActivity(android.content.Intent(this, WebhookSettingsActivity::class.java)) }
        b.cardStats.setOnClickListener { refresh() }
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

    private fun refresh() {
        Toast.makeText(this, getString(R.string.refreshed), Toast.LENGTH_SHORT).show()
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

    private data class StatsData(
        val totalCount: Int,
        val totalAmount: Double,
        val unreadCount: Int
    )
}
