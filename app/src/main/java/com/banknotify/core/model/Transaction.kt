package com.banknotify.core.model

data class Transaction(
    val id: Long = 0,
    val bankCode: String,
    val bankName: String,
    val accountNumber: String,
    val amount: Double,
    val balance: Double? = null,
    val content: String,
    val senderName: String? = null,
    val senderAccount: String? = null,
    val referenceNumber: String? = null,
    val transactionDate: Long = System.currentTimeMillis(),
    val rawMessage: String,
    val status: TransactionStatus = TransactionStatus.PENDING
)

enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    EXPIRED,
    FAILED
}

data class TransactionFilter(
    val bankCode: String? = null,
    val status: TransactionStatus? = null,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val searchContent: String? = null,
    val limit: Int = 50,
    val offset: Int = 0
)
