package com.banknotify.parser

import com.banknotify.model.Transaction

class SacombankParser : BankParser {
    override val bankCode = "STB"
    override val bankName = "Sacombank"
    override val packageNames = listOf("com.sacombank", "vn.sacombank", "com.sacombank.app")

    override fun parse(title: String, body: String): Transaction? {
        val text = "$title $body"
        if (!text.contains("Sacombank", ignoreCase = true) && !text.contains("STB", ignoreCase = true)) return null

        val amount = extractAmount(text) ?: return null
        val accountNumber = extractAccountNumber(text)
        val content = extractContent(text) ?: ""
        val senderName = extractSenderName(text)
        val referenceNumber = extractReference(text)

        return Transaction(
            bankCode = bankCode, bankName = bankName,
            accountNumber = accountNumber ?: "",
            amount = amount, content = content, senderName = senderName,
            referenceNumber = referenceNumber,
            transactionDate = System.currentTimeMillis(), rawMessage = text
        )
    }

    private fun extractAmount(text: String): Double? {
        val p = Regex("""([+-]?[\d,.]+)\s*VNĐ?""", RegexOption.IGNORE_CASE)
        return p.find(text)?.let { parseAmount(it.groupValues[1]) }
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

    private fun extractReference(text: String): String? {
        val p = Regex("""(?:MGD|GD)[:\s]*([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun parseAmount(s: String): Double {
        return s.replace("[^\\d.,]".toRegex()).replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
