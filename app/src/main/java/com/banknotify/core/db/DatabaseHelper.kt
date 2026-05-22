package com.banknotify.core.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.sqlite.db.SimpleSQLiteQuery
import com.banknotify.core.model.Account
import com.banknotify.core.model.AccountBalance
import com.banknotify.core.model.CashFlow
import com.banknotify.core.model.CategorySummary
import com.banknotify.core.model.MonthlyStat
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionFilter
import com.banknotify.core.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

class DatabaseHelper(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.transactionDao()
    private val accountDao = db.accountDao()

    fun insertTransaction(tx: Transaction): Long = dao.insert(tx)

    fun getTransaction(id: Long): Transaction? = dao.getById(id)

    fun getTransactions(filter: TransactionFilter): List<Transaction> {
        val query = buildFilterQuery(filter)
        val sqldb = db.openHelper.writableDatabase
        val args = query.args + arrayOf(filter.limit.toString(), filter.offset.toString())
        val cursor = sqldb.query(SimpleSQLiteQuery(
            "SELECT * FROM transactions ${query.where} ORDER BY transaction_date DESC LIMIT ? OFFSET ?",
            args
        ))
        val result = mutableListOf<Transaction>()
        while (cursor.moveToNext()) {
            result.add(cursorToTransaction(cursor))
        }
        cursor.close()
        return result
    }

    fun getRecentTransactions(limit: Int = 20, offset: Int = 0): List<Transaction> =
        dao.getRecent(limit, offset)

    fun observeTransactions(limit: Int = 20, offset: Int = 0): Flow<List<Transaction>> =
        dao.observeRecent(limit, offset)

    fun getTransactionPagingSource(): PagingSource<Int, Transaction> = dao.getPagingSource()

    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    fun observeTotalCount(): Flow<Int> = dao.observeTotalCount()

    fun observeTotalAmount(): Flow<Double> = dao.observeTotalAmount()

    fun updateStatus(id: Long, status: TransactionStatus) = dao.updateStatus(id, status)

    fun updateCategory(id: Long, category: String) = dao.updateCategory(id, category)

    fun updateNote(id: Long, note: String) = dao.updateNote(id, note)

    fun getTransactionByReference(ref: String): Transaction? = dao.getByReference(ref)

    fun getUnreadCount(): Int = dao.getUnreadCount()

    fun getTotalTransactions(): Int = dao.getTotalCount()

    fun getTotalAmount(): Double = dao.getTotalAmount()

    fun deleteAllTransactions(): Int {
        val count = dao.getTotalCount()
        dao.deleteAll()
        return count
    }

    fun deleteTransaction(id: Long) = dao.deleteById(id)

    fun observeMonthlyStats(): Flow<List<MonthlyStat>> = dao.observeMonthlyStats()

    fun getMonthlyStats(): List<MonthlyStat> {
        val sqldb = db.openHelper.writableDatabase
        val cursor = sqldb.query(SimpleSQLiteQuery(
            "SELECT strftime('%Y-%m', transaction_date / 1000, 'unixepoch') as month, COUNT(*) as count, COALESCE(SUM(amount), 0) as total FROM transactions GROUP BY month ORDER BY month DESC LIMIT 12"))
        val result = mutableListOf<MonthlyStat>()
        while (cursor.moveToNext()) {
            result.add(MonthlyStat(cursor.getString(0), cursor.getInt(1), cursor.getDouble(2)))
        }
        cursor.close()
        return result
    }

    fun getCountSince(since: Long): Int = dao.getCountSince(since)

    fun getAmountSince(since: Long): Double = dao.getAmountSince(since)

    fun getCategorySummaries(): List<CategorySummary> = dao.getCategorySummaries()

    fun getIncomeByCategory(): List<CategorySummary> = dao.getIncomeByCategory()

    fun getExpenseByCategory(): List<CategorySummary> = dao.getExpenseByCategory()

    fun getMonthlyCashFlow(): List<CashFlow> = dao.getMonthlyCashFlow()

    fun getBalancesByType(): List<AccountBalance> = dao.getBalancesByType()

    fun getIncomeThisMonth(): Double {
        val start = monthStart()
        val sqldb = db.openHelper.writableDatabase
        val cursor = sqldb.query(SimpleSQLiteQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE amount > 0 AND transaction_date >= ?",
            arrayOf(start.toString())
        ))
        cursor.moveToFirst()
        val result = cursor.getDouble(0)
        cursor.close()
        return result
    }

    fun getExpenseThisMonth(): Double {
        val start = monthStart()
        val sqldb = db.openHelper.writableDatabase
        val cursor = sqldb.query(SimpleSQLiteQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE amount < 0 AND transaction_date >= ?",
            arrayOf(start.toString())
        ))
        cursor.moveToFirst()
        val result = cursor.getDouble(0)
        cursor.close()
        return result
    }

    fun getIncomeThisMonthCount(): Int {
        val start = monthStart()
        val sqldb = db.openHelper.writableDatabase
        val cursor = sqldb.query(SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM transactions WHERE amount > 0 AND transaction_date >= ?",
            arrayOf(start.toString())
        ))
        cursor.moveToFirst()
        val result = cursor.getInt(0)
        cursor.close()
        return result
    }

    fun getExpenseThisMonthCount(): Int {
        val start = monthStart()
        val sqldb = db.openHelper.writableDatabase
        val cursor = sqldb.query(SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM transactions WHERE amount < 0 AND transaction_date >= ?",
            arrayOf(start.toString())
        ))
        cursor.moveToFirst()
        val result = cursor.getInt(0)
        cursor.close()
        return result
    }

    fun getTotalAssets(): Double = accountDao.getTotalAssets()

    fun getAccounts(): List<Account> = accountDao.getAll()

    fun getAccountsByType(type: String): List<Account> = accountDao.getByType(type)

    fun insertAccount(account: Account): Long = accountDao.insert(account)

    fun updateAccountBalance(id: Long, balance: Double) = accountDao.updateBalance(id, balance)

    fun updateAccountOpeningBalance(id: Long, balance: Double) = accountDao.updateOpeningBalance(id, balance)

    private fun buildFilterQuery(filter: TransactionFilter): FilterQuery {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        filter.bankCode?.let { conditions.add("bank_code = ?"); args.add(it) }
        filter.status?.let { conditions.add("status = ?"); args.add(it.name) }
        filter.fromDate?.let { conditions.add("transaction_date >= ?"); args.add(it.toString()) }
        filter.toDate?.let { conditions.add("transaction_date <= ?"); args.add(it.toString()) }
        filter.minAmount?.let { conditions.add("amount >= ?"); args.add(it.toString()) }
        filter.maxAmount?.let { conditions.add("amount <= ?"); args.add(it.toString()) }
        filter.searchContent?.let { conditions.add("content LIKE ?"); args.add("%$it%") }
        filter.accountType?.let { conditions.add("account_type = ?"); args.add(it) }
        filter.category?.let { conditions.add("category = ?"); args.add(it) }

        val where = if (conditions.isNotEmpty()) "WHERE ${conditions.joinToString(" AND ")}" else ""
        return FilterQuery(where, args.toTypedArray())
    }

    private fun cursorToTransaction(c: android.database.Cursor): Transaction = Transaction(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        bankCode = c.getString(c.getColumnIndexOrThrow("bank_code")),
        bankName = c.getString(c.getColumnIndexOrThrow("bank_name")),
        accountNumber = c.getString(c.getColumnIndexOrThrow("account_number")),
        amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
        balance = if (c.isNull(c.getColumnIndexOrThrow("balance"))) null else c.getDouble(c.getColumnIndexOrThrow("balance")),
        content = c.getString(c.getColumnIndexOrThrow("content")),
        senderName = c.getString(c.getColumnIndexOrThrow("sender_name")),
        senderAccount = c.getString(c.getColumnIndexOrThrow("sender_account")),
        referenceNumber = c.getString(c.getColumnIndexOrThrow("reference_number")),
        transactionDate = c.getLong(c.getColumnIndexOrThrow("transaction_date")),
        rawMessage = c.getString(c.getColumnIndexOrThrow("raw_message")),
        status = TransactionStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
        accountType = c.getString(c.getColumnIndexOrThrow("account_type")),
        category = c.getString(c.getColumnIndexOrThrow("category")),
        tags = c.getString(c.getColumnIndexOrThrow("tags")),
        note = c.getString(c.getColumnIndexOrThrow("note"))
    )

    private fun monthStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private data class FilterQuery(val where: String, val args: Array<String>)
}
