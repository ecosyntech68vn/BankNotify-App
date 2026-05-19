package com.banknotify.parser

import com.banknotify.model.Transaction

class BIDVParser : BankParser {
    override val bankCode = "BIDV"
    override val bankName = "BIDV"
    override val packageNames = listOf("com.bidv", "vn.bidv", "com.bidv.app")

    override fun parse(title: String, body: String): Transaction? {
        val text = "$title $body"
        if (!text.contains("BIDV", ignoreCase = true)) return null

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
            transactionDate = System.currentTimeMillis(), rawMessage = text
        )
    }

    private fun extractAmount(text: String): Double? {
        val p = Regex("""([+-]?[\d,.]+)\s*VND""", RegexOption.IGNORE_CASE)
        val m = p.find(text)
        if (m != null) {
            val v = parseAmount(m.groupValues[1])
            if (v > 0) return v
        }
        val p2 = Regex("""số[_\s]?tiền[:\s]*([\d,.]+)""", RegexOption.IGNORE_CASE)
        return p2.find(text)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractAccountNumber(text: String): String? {
        val p = Regex("""(?:TK|tài khoản)[:\s]*(\d{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun extractContent(text: String): String? {
        val p = Regex("""(?:ND|nội dung|ct)[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun extractSenderName(text: String): String? {
        val p = Regex("""([A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+){1,5})\s*(?:CK|chuyển|chuyển khoản)""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun extractBalance(text: String): Double? {
        val p = Regex("""(?:SD|số dư)[:\s]*([\d,.]+)""", RegexOption.IGNORE_CASE)
        return p.find(text)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractReference(text: String): String? {
        val p = Regex("""(?:MGD|GD|giao dịch)[:\s]*([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun parseAmount(s: String): Double {
        return s.replace("[^\\d.,]".toRegex()).replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
