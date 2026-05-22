package com.banknotify.core.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
