package com.banknotify.core

import com.banknotify.core.model.Transaction

object CategoryEngine {

    private data class Rule(
        val keywords: List<String>,
        val category: String?,
        val isIncome: Boolean? = null
    )

    private val rules = listOf(
        Rule(listOf("lương", "salary", "salary", "lương tháng"), "SALARY", isIncome = true),
        Rule(listOf("chuyển tiền", "chuyen tien", "ck", "chuyển khoản"), "TRANSFER"),
        Rule(listOf("mua hàng", "mua hang", "thanh toán", "thanh toan", "hoá đơn", "hóa đơn", "shopping"), "SHOPPING", isIncome = false),
        Rule(listOf("rút tiền", "rut tien", "withdraw"), "WITHDRAWAL", isIncome = false),
        Rule(listOf("nạp tiền", "nạp", "deposit"), "DEPOSIT", isIncome = true),
        Rule(listOf("ăn uống", "an uong", "food", "cf", "cafe", "coffee", "trà sữa", "tra sua"), "FOOD", isIncome = false),
        Rule(listOf("điện", "nước", "internet", "điện thoại", "tiền nhà", "rent"), "UTILITIES", isIncome = false),
        Rule(listOf("xăng", "gas", "taxi", "grab", "xe", "bus", "transport"), "TRANSPORT", isIncome = false),
        Rule(listOf("bảo hiểm", "insurance", "bh"), "INSURANCE", isIncome = false),
        Rule(listOf("đầu tư", "invest", "chứng khoán", "co phieu"), "INVESTMENT"),
        Rule(listOf("lãi", "interest", "interest"), "INTEREST", isIncome = true),
        Rule(listOf("hoàn tiền", "refund", "cashback"), "REFUND", isIncome = true),
        Rule(listOf("y tế", "benh vien", "hospital", "thuốc", "medicine"), "HEALTH", isIncome = false),
        Rule(listOf("giáo dục", "học phí", "tuition", "hoc phi", "education"), "EDUCATION", isIncome = false),
        Rule(listOf("giải trí", "game", "music", "phim", "movie", "spotify", "netflix"), "ENTERTAINMENT", isIncome = false),
        Rule(listOf("từ thiện", "charity", "quyên góp"), "CHARITY", isIncome = false),
        Rule(listOf("chuyển đến", "nhan", "received from", "nhận"), null, isIncome = true)
    )

    fun categorize(tx: Transaction): String? {
        val text = buildString {
            append(tx.content)
            tx.senderName?.let { append(" $it") }
            tx.referenceNumber?.let { append(" $it") }
        }.lowercase()

        for (rule in rules) {
            if (rule.category == null) {
                if (rule.isIncome != null && amountMatches(rule.isIncome, tx.amount)) {
                    return if (rule.isIncome) "INCOME" else "EXPENSE"
                }
                continue
            }
            if (rule.keywords.any { text.contains(it) }) {
                if (rule.isIncome == null || amountMatches(rule.isIncome, tx.amount)) {
                    return rule.category
                }
            }
        }
        if (tx.amount > 0) return "INCOME"
        if (tx.amount < 0) return "EXPENSE"
        return null
    }

    private fun amountMatches(isIncome: Boolean, amount: Double): Boolean =
        if (isIncome) amount > 0 else amount < 0
}
