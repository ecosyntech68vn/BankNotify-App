package com.banknotify.core.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionFilter
import com.banknotify.core.model.TransactionStatus

class DatabaseHelper(context: android.content.Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_BANK_CODE TEXT NOT NULL,
                $COL_BANK_NAME TEXT NOT NULL,
                $COL_ACCOUNT_NUMBER TEXT NOT NULL,
                $COL_AMOUNT REAL NOT NULL,
                $COL_BALANCE REAL,
                $COL_CONTENT TEXT NOT NULL,
                $COL_SENDER_NAME TEXT,
                $COL_SENDER_ACCOUNT TEXT,
                $COL_REFERENCE_NUMBER TEXT,
                $COL_TRANSACTION_DATE INTEGER NOT NULL,
                $COL_RAW_MESSAGE TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL DEFAULT 'PENDING',
                $COL_CREATED_AT INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX idx_transactions_bank_code ON $TABLE_TRANSACTIONS($COL_BANK_CODE)")
        db.execSQL("CREATE INDEX idx_transactions_date ON $TABLE_TRANSACTIONS($COL_TRANSACTION_DATE)")
        db.execSQL("CREATE INDEX idx_transactions_status ON $TABLE_TRANSACTIONS($COL_STATUS)")
        db.execSQL("CREATE INDEX idx_transactions_reference ON $TABLE_TRANSACTIONS($COL_REFERENCE_NUMBER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (v in oldVersion until newVersion) {
            migrate(db, v + 1)
        }
    }

    private fun migrate(db: SQLiteDatabase, targetVersion: Int) {
        when (targetVersion) {
            // Add future migrations here, e.g.:
            // 2 -> db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COL_NEW_FIELD TEXT")
        }
    }

    fun insertTransaction(tx: Transaction): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BANK_CODE, tx.bankCode)
            put(COL_BANK_NAME, tx.bankName)
            put(COL_ACCOUNT_NUMBER, tx.accountNumber)
            put(COL_AMOUNT, tx.amount)
            if (tx.balance != null) put(COL_BALANCE, tx.balance)
            put(COL_CONTENT, tx.content)
            put(COL_SENDER_NAME, tx.senderName)
            put(COL_SENDER_ACCOUNT, tx.senderAccount)
            put(COL_REFERENCE_NUMBER, tx.referenceNumber)
            put(COL_TRANSACTION_DATE, tx.transactionDate)
            put(COL_RAW_MESSAGE, tx.rawMessage)
            put(COL_STATUS, tx.status.name)
        }
        return db.insertWithOnConflict(TABLE_TRANSACTIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getTransaction(id: Long): Transaction? {
        val db = readableDatabase
        val cursor = db.query(TABLE_TRANSACTIONS, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
        return cursor.use { if (it.moveToFirst()) cursorToTransaction(it) else null }
    }

    fun getTransactions(filter: TransactionFilter): List<Transaction> {
        val db = readableDatabase
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        filter.bankCode?.let { conditions.add("$COL_BANK_CODE = ?"); args.add(it) }
        filter.status?.let { conditions.add("$COL_STATUS = ?"); args.add(it.name) }
        filter.fromDate?.let { conditions.add("$COL_TRANSACTION_DATE >= ?"); args.add(it.toString()) }
        filter.toDate?.let { conditions.add("$COL_TRANSACTION_DATE <= ?"); args.add(it.toString()) }
        filter.minAmount?.let { conditions.add("$COL_AMOUNT >= ?"); args.add(it.toString()) }
        filter.maxAmount?.let { conditions.add("$COL_AMOUNT <= ?"); args.add(it.toString()) }
        filter.searchContent?.let { conditions.add("$COL_CONTENT LIKE ?"); args.add("%$it%") }

        val where = if (conditions.isNotEmpty()) conditions.joinToString(" AND ") else null
        val whereArgs = if (args.isNotEmpty()) args.toTypedArray() else null

        val cursor = db.query(TABLE_TRANSACTIONS, null, where, whereArgs, null, null, "$COL_TRANSACTION_DATE DESC", "${filter.limit} OFFSET ${filter.offset}")
        return cursor.use {
            val result = mutableListOf<Transaction>()
            while (it.moveToNext()) result.add(cursorToTransaction(it))
            result
        }
    }

    fun getRecentTransactions(limit: Int = 20, offset: Int = 0): List<Transaction> {
        return getTransactions(TransactionFilter(limit = limit, offset = offset))
    }

    fun updateStatus(id: Long, status: TransactionStatus): Int {
        val db = writableDatabase
        val values = ContentValues().apply { put(COL_STATUS, status.name) }
        return db.update(TABLE_TRANSACTIONS, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getTransactionByReference(ref: String): Transaction? {
        val db = readableDatabase
        val cursor = db.query(TABLE_TRANSACTIONS, null, "$COL_REFERENCE_NUMBER = ?", arrayOf(ref), null, null, null)
        return cursor.use { if (it.moveToFirst()) cursorToTransaction(it) else null }
    }

    fun getUnreadCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_TRANSACTIONS WHERE $COL_STATUS = ?", arrayOf(TransactionStatus.PENDING.name))
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getTotalTransactions(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_TRANSACTIONS", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getTotalAmount(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE_TRANSACTIONS", null)
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun deleteAllTransactions(): Int {
        val db = writableDatabase
        return db.delete(TABLE_TRANSACTIONS, null, null)
    }

    fun deleteTransaction(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_TRANSACTIONS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    private fun cursorToTransaction(cursor: Cursor): Transaction {
        return Transaction(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            bankCode = cursor.getString(cursor.getColumnIndexOrThrow(COL_BANK_CODE)),
            bankName = cursor.getString(cursor.getColumnIndexOrThrow(COL_BANK_NAME)),
            accountNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_ACCOUNT_NUMBER)),
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_AMOUNT)),
            balance = if (cursor.isNull(cursor.getColumnIndexOrThrow(COL_BALANCE))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(COL_BALANCE)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT)),
            senderName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER_NAME)),
            senderAccount = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER_ACCOUNT)),
            referenceNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_REFERENCE_NUMBER)),
            transactionDate = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TRANSACTION_DATE)),
            rawMessage = cursor.getString(cursor.getColumnIndexOrThrow(COL_RAW_MESSAGE)),
            status = TransactionStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)))
        )
    }

    companion object {
        const val DB_NAME = "banknotify.db"
        const val DB_VERSION = 1

        const val TABLE_TRANSACTIONS = "transactions"
        const val COL_ID = "id"
        const val COL_BANK_CODE = "bank_code"
        const val COL_BANK_NAME = "bank_name"
        const val COL_ACCOUNT_NUMBER = "account_number"
        const val COL_AMOUNT = "amount"
        const val COL_BALANCE = "balance"
        const val COL_CONTENT = "content"
        const val COL_SENDER_NAME = "sender_name"
        const val COL_SENDER_ACCOUNT = "sender_account"
        const val COL_REFERENCE_NUMBER = "reference_number"
        const val COL_TRANSACTION_DATE = "transaction_date"
        const val COL_RAW_MESSAGE = "raw_message"
        const val COL_STATUS = "status"
        const val COL_CREATED_AT = "created_at"
    }
}
