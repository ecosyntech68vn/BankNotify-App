package com.banknotify.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.banknotify.R
import com.banknotify.core.db.DatabaseHelper
import com.banknotify.databinding.ActivityDashboardBinding
import com.banknotify.ui.view.SimpleBarChart
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityDashboardBinding

    @Inject lateinit var dbHelper: DatabaseHelper

    private val currencyFmt = NumberFormat.getNumberInstance(Locale("vi", "VN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.menu_dashboard)

        loadData()
    }

    @Deprecated("Deprecated in Java")
    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }

    private fun loadData() {
        lifecycleScope.launch {
            val (
                netWorth, incomeAmt, expenseAmt, incomeCnt, expenseCnt,
                cashFlows, categories, accounts, monthlyStats
            ) = withContext(Dispatchers.IO) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                val nw = dbHelper.getTotalAssets()
                val ia = dbHelper.getIncomeThisMonth()
                val ea = dbHelper.getExpenseThisMonth()
                val ic = dbHelper.getIncomeThisMonthCount()
                val ec = dbHelper.getExpenseThisMonthCount()
                val cf = dbHelper.getMonthlyCashFlow()
                val cat = dbHelper.getExpenseByCategory()
                val acc = dbHelper.getAccounts()
                val ms = dbHelper.getMonthlyStats()

                arrayOf(nw, ia, ea, ic.toDouble(), ec.toDouble(), cf, cat, acc, ms)
            }

            val nw = netWorth as Double
            val ia = incomeAmt as Double
            val ea = expenseAmt as Double
            val ic = (incomeCnt as Double).toInt()
            val ec = (expenseCnt as Double).toInt()

            b.netWorth.text = "${formatMoney(nw)} VND"

            b.incomeAmount.text = formatMoney(ia)
            b.incomeCount.text = "$ic GD"
            b.expenseAmount.text = formatMoney(-ea)
            b.expenseCount.text = "$ec GD"

            val total = ia + (-ea)
            if (total > 0) {
                val ratio = (ia / total * 100).toInt().coerceIn(1, 99)
                val lp = LinearLayout.LayoutParams(0, 12, ratio.toFloat())
                val rp = LinearLayout.LayoutParams(0, 12, (100 - ratio).toFloat())
                b.incomeBar.layoutParams = lp
                b.expenseBar.layoutParams = rp
            }

            @Suppress("UNCHECKED_CAST")
            val cats = categories as List<com.banknotify.core.model.CategorySummary>
            if (cats.isNotEmpty()) {
                b.categoryCard.visibility = android.view.View.VISIBLE
                b.categoryList.removeAllViews()
                val maxAmt = cats.maxOf { -it.totalAmount }
                for (cat in cats) {
                    val row = layoutInflater.inflate(R.layout.item_category_row, b.categoryList, false)
                    val nameView = row.findViewById<TextView>(R.id.cat_name)
                    val barView = row.findViewById<android.view.View>(R.id.cat_bar)
                    val amountView = row.findViewById<TextView>(R.id.cat_amount)
                    nameView.text = getCategoryLabel(cat.category)
                    amountView.text = formatMoney(-cat.totalAmount)
                    if (maxAmt > 0) {
                        val w = ((-cat.totalAmount / maxAmt) * 100).toInt().coerceIn(5, 100)
                        barView.layoutParams = LinearLayout.LayoutParams(0, 16, w.toFloat())
                    }
                    b.categoryList.addView(row)
                }
            }

            @Suppress("UNCHECKED_CAST")
            val accs = accounts as List<com.banknotify.core.model.Account>
            if (accs.isNotEmpty()) {
                b.accountCard.visibility = android.view.View.VISIBLE
                b.accountList.removeAllViews()
                for (acct in accs) {
                    val row = LinearLayout(this@DashboardActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8, 0, 8)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    row.addView(TextView(this@DashboardActivity).apply {
                        text = acct.name
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    })
                    row.addView(TextView(this@DashboardActivity).apply {
                        text = "${formatMoney(acct.currentBalance)} VND"
                        textSize = 15f
                        gravity = Gravity.END
                    })
                    b.accountList.addView(row)
                }
            }

            @Suppress("UNCHECKED_CAST")
            val cfList = cashFlows as List<com.banknotify.core.model.CashFlow>
            if (cfList.isNotEmpty()) {
                val bars = cfList.take(12).map { cf ->
                    val net = cf.net.toFloat()
                    val color = if (net >= 0) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
                    SimpleBarChart.Bar(cf.period, net, color)
                }
                b.barChart.bars = bars
            } else {
                @Suppress("UNCHECKED_CAST")
                val msList = monthlyStats as List<com.banknotify.core.model.MonthlyStat>
                val colors = arrayOf(
                    0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
                    0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
                    0xFFFF5722.toInt(), 0xFF607D8B.toInt(), 0xFF795548.toInt(),
                    0xFF3F51B5.toInt(), 0xFF009688.toInt(), 0xFFFFEB3B.toInt()
                )
                val bars = msList.mapIndexed { i, stat ->
                    SimpleBarChart.Bar(stat.month, stat.total.toFloat(), colors[i % colors.size])
                }
                b.barChart.bars = bars
            }
        }
    }

    private fun formatMoney(v: Double): String = currencyFmt.format(v)

    private fun getCategoryLabel(cat: String): String = when (cat) {
        "SALARY" -> "Lương"
        "TRANSFER" -> "Chuyển tiền"
        "SHOPPING" -> "Mua sắm"
        "WITHDRAWAL" -> "Rút tiền"
        "DEPOSIT" -> "Nạp tiền"
        "FOOD" -> "Ăn uống"
        "UTILITIES" -> "Tiện ích"
        "TRANSPORT" -> "Di chuyển"
        "INSURANCE" -> "Bảo hiểm"
        "INVESTMENT" -> "Đầu tư"
        "INTEREST" -> "Lãi"
        "REFUND" -> "Hoàn tiền"
        "HEALTH" -> "Y tế"
        "EDUCATION" -> "Giáo dục"
        "ENTERTAINMENT" -> "Giải trí"
        "CHARITY" -> "Từ thiện"
        "INCOME" -> "Thu nhập"
        "EXPENSE" -> "Chi tiêu"
        else -> cat
    }
}
