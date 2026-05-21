package com.banknotify.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionStatus

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tx: Transaction): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC LIMIT :limit OFFSET :offset")
    fun getRecent(limit: Int, offset: Int): List<Transaction>

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: TransactionStatus)

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
}
