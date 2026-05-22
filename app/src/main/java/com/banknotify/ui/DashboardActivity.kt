package com.banknotify.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.banknotify.core.db.DatabaseHelper
import com.banknotify.R
import com.banknotify.databinding.ActivityDashboardBinding
import com.banknotify.ui.view.SimpleBarChart
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityDashboardBinding

    @Inject lateinit var dbHelper: DatabaseHelper

    private val colors = arrayOf(
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
        0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
        0xFFFF5722.toInt(), 0xFF607D8B.toInt(), 0xFF795548.toInt(),
        0xFF3F51B5.toInt(), 0xFF009688.toInt(), 0xFFFFEB3B.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.menu_dashboard)

        loadData()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }

    private fun loadData() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis

        lifecycleScope.launch {
            val txs = dbHelper.getMonthlyStats()
            val bars = txs.mapIndexed { i, stat ->
                SimpleBarChart.Bar(stat.month, stat.total.toFloat(), colors[i % colors.size])
            }
            b.barChart.bars = bars
        }

        b.monthCount.text = dbHelper.getCountSince(monthStart).toString()
        b.monthAmount.text = String.format("%,.0f VND", dbHelper.getAmountSince(monthStart))

        lifecycleScope.launch {
            dbHelper.observeTotalCount().collectLatest { b.allCount.text = it.toString() }
        }
        lifecycleScope.launch {
            dbHelper.observeTotalAmount().collectLatest { b.allAmount.text = String.format("%,.0f VND", it) }
        }
    }
}
