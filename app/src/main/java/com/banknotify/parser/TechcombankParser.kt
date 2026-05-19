package com.banknotify.parser

import com.banknotify.model.Transaction

class TechcombankParser : BankParser {
    override val bankCode = "TCB"
    override val bankName = "Techcombank"
    override val packageNames = listOf(
        "com.techcombank",
        "com.techcombank.techcombankapp",
        "vn.techcombank"
    )

    override fun parse(title: String, body: String): Transaction? {
        val text = "$title $body"
        if (!text.contains("Techcombank", ignoreCase = true) && !text.contains("TCB", ignoreCase = true)) return null
        if (!text.contains("giao dịch", ignoreCase = true) && !text.contains("số tiền", ignoreCase = true)
            && !text.contains("tài khoản", ignoreCase = true) && !text.contains("so tien", ignoreCase = true)) return null

        val amount = extractAmount(text) ?: return null
        val accountNumber = extractAccountNumber(text)
        val content = extractContent(text) ?: ""
        val senderName = extractSenderName(text)
        val balance = extractBalance(text)
        val referenceNumber = extractReference(text)

        return Transaction(
            bankCode = bankCode, bankName = bankName,
            accountNumber = accountNumber ?: "",
            amount = amount, balance = balance,
            content = content, senderName = senderName,
            referenceNumber = referenceNumber,
            transactionDate = System.currentTimeMillis(),
            rawMessage = text
        )
    }

    private fun extractAmount(text: String): Double? {
        val p = Regex("""số[_\s]?tiền[:\s]*([+-]?[\d,.]+)""", RegexOption.IGNORE_CASE)
        return p.find(text)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractAccountNumber(text: String): String? {
        val p = Regex("""tài[_\s]?khoản[:\s]*(\d{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun extractContent(text: String): String? {
        val p = Regex("""nội[_\s]?dung[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun extractSenderName(text: String): String? {
        val p = Regex("""từ\s+([A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+){1,5})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun extractBalance(text: String): Double? {
        val p = Regex("""(?:SD|số dư|so du)[:\s]*([+-]?[\d,.]+)""", RegexOption.IGNORE_CASE)
        return p.find(text)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractReference(text: String): String? {
        val p = Regex("""(?:GD|giao dịch|MGD)[:\s]*([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun parseAmount(s: String): Double {
        return s.replace("[^\\d.,]".toRegex()).replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
