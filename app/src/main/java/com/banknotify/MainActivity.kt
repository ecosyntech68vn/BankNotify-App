package com.banknotify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.banknotify.listener.BankNotificationListener
import com.banknotify.model.Transaction
import com.banknotify.model.TransactionFilter
import com.banknotify.server.ApiServerService
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var serverPortText: TextView
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var serverSwitch: SwitchMaterial
    private lateinit var transactionCountText: TextView
    private lateinit var totalAmountText: TextView
    private lateinit var unreadCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        serverStatusText = findViewById(R.id.server_status_text)
        serverPortText = findViewById(R.id.server_port_text)
        notificationSwitch = findViewById(R.id.notification_switch)
        serverSwitch = findViewById(R.id.server_switch)
        transactionCountText = findViewById(R.id.transaction_count)
        totalAmountText = findViewById(R.id.total_amount)
        unreadCountText = findViewById(R.id.unread_count)
        recyclerView = findViewById(R.id.recycler_transactions)

        adapter = TransactionAdapter { tx -> showTransactionDetail(tx) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupSwitches()
        updateStatus()
        updateStats()

        findViewById<MaterialCardView>(R.id.card_permissions).setOnClickListener {
            openPermissionSettings()
        }

        findViewById<MaterialCardView>(R.id.card_webhook).setOnClickListener {
            startActivity(Intent(this, WebhookSettingsActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.card_stats).setOnClickListener {
            refreshData()
        }

        findViewById<MaterialCardView>(R.id.card_clear).setOnClickListener {
            showClearConfirmDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateStats()
        loadRecentTransactions()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_api_docs -> {
                showApiDocsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupSwitches() {
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!BankNotificationListener.isNotificationListenerEnabled(this)) {
                    BankNotificationListener.openNotificationListenerSettings(this)
                    notificationSwitch.isChecked = false
                }
            } else {
                Toast.makeText(this, "Tắt giám sát: vào Cài đặt > Truy cập đặc biệt > Notification Listener", Toast.LENGTH_LONG).show()
            }
        }

        serverSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val port = getSharedPreferences("server_prefs", MODE_PRIVATE)
                    .getInt("server_port", 8765)
                ApiServerService.start(this, port)
                Toast.makeText(this, "Server đang chạy trên port $port", Toast.LENGTH_SHORT).show()
            } else {
                ApiServerService.stop(this)
                Toast.makeText(this, "Server đã tắt", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }
    }

    private fun updateStatus() {
        val listenerEnabled = BankNotificationListener.isNotificationListenerEnabled(this)
        notificationSwitch.isChecked = listenerEnabled
        statusText.text = if (listenerEnabled) "Đang giám sát" else "Chưa được cấp quyền"
        statusText.setTextColor(
            if (listenerEnabled) ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )

        val prefs = getSharedPreferences("server_prefs", MODE_PRIVATE)
        val port = prefs.getInt("server_port", 8765)
        serverPortText.text = "Port: $port"
    }

    private fun updateStats() {
        val db = BankNotifyApp.instance.dbHelper
        transactionCountText.text = db.getTotalTransactions().toString()
        totalAmountText.text = String.format("%,.0f VND", db.getTotalAmount())
        unreadCountText.text = db.getUnreadCount().toString()
    }

    private fun loadRecentTransactions() {
        val transactions = BankNotifyApp.instance.dbHelper.getRecentTransactions(20, 0)
        adapter.submitList(transactions)
    }

    private fun refreshData() {
        updateStats()
        loadRecentTransactions()
        Toast.makeText(this, "Đã làm mới", Toast.LENGTH_SHORT).show()
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xoá dữ liệu")
            .setMessage("Bạn có chắc muốn xoá tất cả giao dịch?")
            .setPositiveButton("Xoá") { _, _ ->
                val db = BankNotifyApp.instance.dbHelper
                val txs = db.getRecentTransactions(Int.MAX_VALUE, 0)
                txs.forEach { db.deleteTransaction(it.id) }
                refreshData()
                Toast.makeText(this, "Đã xoá tất cả giao dịch", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun showTransactionDetail(tx: Transaction) {
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

        AlertDialog.Builder(this)
            .setTitle("Chi tiết giao dịch")
            .setMessage(detail)
            .setPositiveButton("OK", null)
            .setNeutralButton("Xác nhận") { _, _ ->
                BankNotifyApp.instance.dbHelper.updateStatus(tx.id, com.banknotify.model.TransactionStatus.CONFIRMED)
                refreshData()
                Toast.makeText(this, "Đã xác nhận giao dịch", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("BankNotify")
            .setMessage("""
                Phiên bản: 1.0
                
                Ứng dụng đọc thông báo từ các ứng dụng ngân hàng để xác thực thanh toán khách hàng tự động.
                
                Hỗ trợ: Vietcombank, Techcombank, MB Bank, ACB, VPBank, TPBank, VIB, BIDV, VietinBank, Sacombank và các ngân hàng khác.
                
                Cách dùng:
                1. Cấp quyền Notification Listener
                2. Bật server API
                3. Cấu hình webhook để nhận callback
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showApiDocsDialog() {
        val prefs = getSharedPreferences("server_prefs", MODE_PRIVATE)
        val port = prefs.getInt("server_port", 8765)
        AlertDialog.Builder(this)
            .setTitle("API Documentation")
            .setMessage("""
                Base URL: http://<device_ip>:$port/api
                
                GET  /health               - Kiểm tra server
                GET  /transactions         - Danh sách giao dịch
                GET  /transactions/recent  - Giao dịch gần đây
                GET  /transactions/<id>    - Chi tiết giao dịch
                POST /transactions/<id>/confirm - Xác nhận GD
                GET  /transactions/stats   - Thống kê
                GET  /transactions/unread  - Số GD chưa đọc
                
                GET  /webhook              - Xem webhook config
                POST /webhook              - Cập nhật webhook
                POST /webhook/test         - Test webhook
                
                GET  /config               - Xem config
                POST /config               - Cập nhật config
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
