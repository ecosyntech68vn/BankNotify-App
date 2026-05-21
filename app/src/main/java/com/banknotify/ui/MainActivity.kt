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
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionStatus
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
        supportActionBar?.title = "BankNotify"

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
            .setTitle("Ứng dụng vừa bị lỗi")
            .setMessage("BankNotify đã gặp lỗi không mong muốn. Bạn có muốn gửi báo cáo lỗi để giúp cải thiện ứng dụng không?")
            .setPositiveButton("Gửi báo cáo") { _, _ ->
                com.banknotify.core.crash.CrashReporter.shareCrashReport(this, latest)
            }
            .setNeutralButton("Xem chi tiết") { _, _ ->
                val content = com.banknotify.core.crash.CrashReporter.getCrashContent(latest)
                AlertDialog.Builder(this)
                    .setTitle("Chi tiết lỗi")
                    .setMessage(content)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Gửi") { _, _ ->
                        com.banknotify.core.crash.CrashReporter.shareCrashReport(this, latest)
                    }.show()
            }
            .setNegativeButton("Bỏ qua") { _, _ ->
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
                b.totalAmount.text = String.format("%,.0f VND", data.totalAmount)
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
                Toast.makeText(this, "Tắt giám sát: vào Cài đặt > Truy cập đặc biệt > Notification Listener", Toast.LENGTH_LONG).show()
            }
        }
        b.serverSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(BankNotifyApp.PREF_SERVER, android.content.Context.MODE_PRIVATE)
            val port = prefs.getInt(BankNotifyApp.KEY_SERVER_PORT, BankNotifyApp.DEFAULT_PORT)
            if (isChecked) {
                ApiServerService.start(this, port)
                Toast.makeText(this, "Server đang chạy port $port", Toast.LENGTH_SHORT).show()
            } else {
                ApiServerService.stop(this)
                Toast.makeText(this, "Server đã tắt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatus() {
        val enabled = BankNotificationListener.isNotificationListenerEnabled(this)
        b.notificationSwitch.isChecked = enabled
        b.statusText.text = if (enabled) "Đang giám sát" else "Chưa được cấp quyền"
        b.statusText.setTextColor(
            if (enabled) androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        val prefs = getSharedPreferences(BankNotifyApp.PREF_SERVER, android.content.Context.MODE_PRIVATE)
        b.serverPortText.text = "Port: ${prefs.getInt(BankNotifyApp.KEY_SERVER_PORT, BankNotifyApp.DEFAULT_PORT)}"
        b.serverSwitch.isChecked = ApiServerService.isRunning
    }

    private fun refresh() {
        Toast.makeText(this, "Đã làm mới", Toast.LENGTH_SHORT).show()
    }

    private fun showClearDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xoá dữ liệu")
            .setMessage("Bạn có chắc muốn xoá tất cả giao dịch?")
            .setPositiveButton("Xoá") { _, _ ->
                dbHelper.deleteAllTransactions()
                Toast.makeText(this, "Đã xoá", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Huỷ", null).show()
    }

    private fun showDetail(tx: Transaction) {
        val detail = """
            Ngân hàng: ${tx.bankName} (${tx.bankCode})
            Số TK: ${tx.accountNumber}
            Số tiền: ${String.format("%,.0f", tx.amount)} VND
            Người gửi: ${tx.senderName ?: "N/A"}
            Nội dung: ${tx.content}
            Mã GD: ${tx.referenceNumber ?: "N/A"}
            Số dư: ${tx.balance?.let { String.format("%,.0f", it) + " VND" } ?: "N/A"}
            Thời gian: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(tx.transactionDate))}
            Trạng thái: ${tx.status.name}
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Chi tiết giao dịch").setMessage(detail)
            .setPositiveButton("OK", null)
            .setNeutralButton("Xác nhận") { _, _ ->
                dbHelper.updateStatus(tx.id, TransactionStatus.CONFIRMED)
                Toast.makeText(this, "Đã xác nhận", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showUpdateDialog() {
        val url = com.banknotify.update.UpdateManager.getUpdateCheckUrl()
        val input = android.widget.EditText(this).apply { setText(url); hint = "URL kiểm tra cập nhật" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cập nhật phần mềm")
            .setMessage("Nhập URL server cập nhật (JSON)")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                com.banknotify.update.UpdateManager.setUpdateCheckUrl(input.text.toString())
                Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Huỷ", null).show()
    }

    private fun showAbout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("BankNotify").setMessage("""
                Phiên bản: ${BankNotifyApp.instance.appVersion}
                Ứng dụng đọc thông báo ngân hàng xác thực thanh toán tự động.
                Hỗ trợ: VCB, TCB, MB, ACB, VPB, TPB, VIB, BIDV, CTG, STB, HDB, OCB, MSB, SHB
                Cách dùng: 1. Cấp quyền Notification Listener 2. Bật server API 3. Cấu hình webhook
            """.trimIndent())
            .setPositiveButton("OK", null).show()
    }

    private fun showApiDocs() {
        val port = getSharedPreferences(BankNotifyApp.PREF_SERVER, android.content.Context.MODE_PRIVATE)
            .getInt(BankNotifyApp.KEY_SERVER_PORT, BankNotifyApp.DEFAULT_PORT)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("API Docs").setMessage("""
                Base: http://<ip>:$port/api/v1

                GET  /health          - Ping
                GET  /transactions    - Filter: bank_code, status, from_date, to_date, min_amount, search, limit, offset
                POST /transactions/{id}/confirm
                GET  /transactions/stats
                GET  /transactions/unread

                GET/POST  /webhook    - Webhook config
                POST /webhook/test    - Test webhook

                GET/POST  /config     - Server config

                GET/POST  /update/check  - Check OTA update
                GET/POST  /update/url    - Update check URL
            """.trimIndent())
            .setPositiveButton("OK", null).show()
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
