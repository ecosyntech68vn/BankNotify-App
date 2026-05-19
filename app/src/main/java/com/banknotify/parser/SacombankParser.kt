package com.banknotify.parser

import com.banknotify.core.model.Transaction
import java.util.regex.Pattern

class SacombankParser : BankParser {
    override val bankCode = "STB"
    override val bankName = "Sacombank"
    override val packageNames = listOf("com.sacombank", "vn.sacombank", "com.sacombank.app")

    override fun parse(title: String, body: String): Transaction? {
        val text = "$title $body"
        if (!text.contains("Sacombank", true)) return null
        val amount = extractAmount(text) ?: return null
        val account = extractAccount(text)
        val content = extractContent(text) ?: ""
        val sender = extractSender(text)
        val balance = extractBalance(text)
        val ref = extractReference(text)
        val date = extractDate(text)
        return Transaction(bankCode = bankCode, bankName = bankName, accountNumber = account ?: "", amount = amount, balance = balance, content = content, senderName = sender, referenceNumber = ref, transactionDate = date, rawMessage = text)
    }

    private fun extractAmount(t: String): Double? {
        val p = Regex("""([+-]?[\d,]+(?:\.\d+)?)\s*VND""", RegexOption.IGNORE_CASE)
        return p.find(t)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractAccount(t: String): String? {
        val p = Regex("""tài[_\s]?khoản[:\s]*(\d{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)
    }

    private fun extractContent(t: String): String? {
        val p = Regex("""nội[_\s]?dung[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)?.trim()
    }

    private fun extractSender(t: String): String? {
        val p = Regex("""([A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+){1,5})\s*(?:chuyển|ck|thanh toán)""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)
    }

    private fun extractBalance(t: String): Double? {
        val p = Regex("""(?:SD|số dư|so du)[:\s]*([+-]?[\d,]+(?:\.\d+)?)\s*VND""", RegexOption.IGNORE_CASE)
        return p.find(t)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractReference(t: String): String? {
        val p = Regex("""(?:GD|giao dịch|MGD)[:\s]*([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)
    }

    private fun extractDate(t: String): Long {
        val p = Regex("""(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})\s*(\d{1,2})[:\s](\d{2})""")
        val m = p.find(t)
        if (m != null) try {
            val cal = java.util.Calendar.getInstance()
            cal.set(m.groupValues[3].toInt(), m.groupValues[2].toInt()-1, m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())
            return cal.timeInMillis
        } catch (_: Exception) {}
        return System.currentTimeMillis()
    }

    private fun parseAmount(s: String): Double = s.replace("[^\\d.,]".toRegex()).replace(",", "").toDoubleOrNull() ?: 0.0
}
