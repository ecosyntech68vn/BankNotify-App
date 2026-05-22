package com.banknotify.core.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.banknotify.core.model.Account
import com.banknotify.core.model.CategorySummary
import com.banknotify.core.model.CashFlow
import com.banknotify.core.model.MonthlyStat
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tx: Transaction): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, id DESC LIMIT :limit OFFSET :offset")
    fun getRecent(limit: Int, offset: Int): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observeRecent(limit: Int, offset: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, id DESC")
    fun getPagingSource(): PagingSource<Int, Transaction>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions")
    fun observeTotalAmount(): Flow<Double>

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: TransactionStatus): Int

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    fun updateCategory(id: Long, category: String): Int

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    fun updateNote(id: Long, note: String): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    fun deleteAll()

    @Query("SELECT * FROM transactions WHERE reference_number = :ref LIMIT 1")
    fun getByReference(ref: String): Transaction?

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun getUnreadCount(): Int

    @Query("SELECT COUNT(*) FROM transactions")
    fun getTotalCount(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions")
    fun getTotalAmount(): Double

    @Query("SELECT strftime('%Y-%m', transaction_date / 1000, 'unixepoch') as month, COUNT(*) as count, COALESCE(SUM(amount), 0) as total FROM transactions GROUP BY month ORDER BY month DESC LIMIT 12")
    fun observeMonthlyStats(): Flow<List<MonthlyStat>>

    @Query("SELECT COUNT(*) FROM transactions WHERE transaction_date >= :since")
    fun getCountSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE transaction_date >= :since")
    fun getAmountSince(since: Long): Double

    @Query("SELECT category, COALESCE(SUM(amount), 0) as totalAmount, COUNT(*) as count FROM transactions WHERE category IS NOT NULL AND category != '' GROUP BY category ORDER BY totalAmount DESC")
    fun getCategorySummaries(): List<CategorySummary>

    @Query("SELECT category, COALESCE(SUM(amount), 0) as totalAmount, COUNT(*) as count FROM transactions WHERE category IS NOT NULL AND category != '' AND amount > 0 GROUP BY category ORDER BY totalAmount DESC")
    fun getIncomeByCategory(): List<CategorySummary>

    @Query("SELECT category, COALESCE(SUM(amount), 0) as totalAmount, COUNT(*) as count FROM transactions WHERE category IS NOT NULL AND category != '' AND amount < 0 GROUP BY category ORDER BY totalAmount ASC")
    fun getExpenseByCategory(): List<CategorySummary>

    @Query("SELECT strftime('%Y-%m', transaction_date / 1000, 'unixepoch') as period, COALESCE(SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END), 0) as income, COALESCE(SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END), 0) as expense, COALESCE(SUM(amount), 0) as net FROM transactions GROUP BY period ORDER BY period DESC LIMIT 12")
    fun getMonthlyCashFlow(): List<CashFlow>

    @Query("SELECT account_type as name, account_type as type, COALESCE(SUM(balance), 0) as balance FROM transactions WHERE balance IS NOT NULL GROUP BY account_type")
    fun getBalancesByType(): List<AccountBalance>
}

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(account: Account): Long

    @Query("SELECT * FROM accounts WHERE active = 1 ORDER BY type, name")
    fun getAll(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE type = :type AND active = 1")
    fun getByType(type: String): List<Account>

    @Query("UPDATE accounts SET current_balance = :balance, last_updated = :now WHERE id = :id")
    fun updateBalance(id: Long, balance: Double, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE accounts SET opening_balance = :balance WHERE id = :id")
    fun updateOpeningBalance(id: Long, balance: Double): Int

    @Query("UPDATE accounts SET active = 0 WHERE id = :id")
    fun deactivate(id: Long): Int

    @Query("SELECT COALESCE(SUM(current_balance), 0) FROM accounts WHERE active = 1")
    fun getTotalAssets(): Double
}
