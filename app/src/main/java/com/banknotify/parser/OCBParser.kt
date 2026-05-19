package com.banknotify.parser

import com.banknotify.model.Transaction

class OCBParser : BankParser {
    override val bankCode = "OCB"
    override val bankName = "OCB"
    override val packageNames = listOf("com.ocb", "vn.ocb", "com.ocb.app", "com.ocb.ocbapp")

    override fun parse(title: String, body: String): Transaction? {
        val text = "$title $body"
        if (!text.contains("OCB", ignoreCase = true)) return null
        val amount = extractAmount(text) ?: return null
        val accountNumber = extractAccountNumber(text)
        val content = extractContent(text) ?: ""
        val senderName = extractSenderName(text)
        return Transaction(bankCode = bankCode, bankName = bankName,
            accountNumber = accountNumber ?: "", amount = amount, content = content,
            senderName = senderName, transactionDate = System.currentTimeMillis(), rawMessage = text)
    }

    private fun extractAmount(text: String): Double? {
        val p = Regex("""([\d,.]+)\s*VND""", RegexOption.IGNORE_CASE)
        return p.find(text)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractAccountNumber(text: String): String? {
        val p = Regex("""(\d{8,20})""").find(text)
        return p?.groupValues?.get(1)
    }

    private fun extractContent(text: String): String? {
        val p = Regex("""(?:ND|nội dung)[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun extractSenderName(text: String): String? {
        val p = Regex("""([A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+){1,5})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun parseAmount(s: String): Double {
        return s.replace("[^\\d.,]".toRegex()).replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
