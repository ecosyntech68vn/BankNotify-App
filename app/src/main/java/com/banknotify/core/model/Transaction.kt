package com.banknotify.core.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "bank_code") val bankCode: String,
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_number") val accountNumber: String,
    val amount: Double,
    val balance: Double? = null,
    val content: String,
    @ColumnInfo(name = "sender_name") val senderName: String? = null,
    @ColumnInfo(name = "sender_account") val senderAccount: String? = null,
    @ColumnInfo(name = "reference_number") val referenceNumber: String? = null,
    @ColumnInfo(name = "transaction_date") val transactionDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "raw_message") val rawMessage: String,
    val status: TransactionStatus = TransactionStatus.PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "account_type") val accountType: String? = null,
    val category: String? = null,
    val tags: String? = null,
    val note: String? = null
)

enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    EXPIRED,
    FAILED
}

data class MonthlyStat(
    val month: String,
    val count: Int,
    val total: Double
)

data class CategorySummary(
    val category: String,
    val totalAmount: Double,
    val count: Int
)

data class NetWorth(
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double
)

data class CashFlow(
    val period: String,
    val income: Double,
    val expense: Double,
    val net: Double
)

data class AccountBalance(
    val name: String,
    val type: String,
    val balance: Double
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String = "BANK",
    @ColumnInfo(name = "account_number") val accountNumber: String? = null,
    @ColumnInfo(name = "bank_code") val bankCode: String? = null,
    @ColumnInfo(name = "opening_balance") val openingBalance: Double = 0.0,
    @ColumnInfo(name = "current_balance") val currentBalance: Double = 0.0,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    val active: Boolean = true
)

data class TransactionFilter(
    val bankCode: String? = null,
    val status: TransactionStatus? = null,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val searchContent: String? = null,
    val accountType: String? = null,
    val category: String? = null,
    val limit: Int = 50,
    val offset: Int = 0
)
