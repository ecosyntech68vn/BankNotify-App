package com.banknotify.core.model

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
