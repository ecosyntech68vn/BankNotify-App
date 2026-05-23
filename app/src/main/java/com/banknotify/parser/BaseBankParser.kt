package com.banknotify.parser

import com.banknotify.core.model.Transaction
import java.util.Calendar

class BaseBankParser(private val config: BankParserConfig) : BankParser {

    override val bankCode = config.bankCode
    override val bankName = config.bankName
    override val packageNames = config.packageNames

    override fun parse(title: String, body: String): Transaction? {
        val text = "$title $body"
        if (config.identifiers.none { text.contains(it, true) }) return null
        val amount = extractAmount(text) ?: return null
        val account = extractAccount(text)
        val content = extractContent(text) ?: ""
        val sender = extractSender(text)
        val balance = extractBalance(text)
        val ref = extractReference(text)
        val date = extractDate(text)
        return Transaction(
            bankCode = bankCode,
            bankName = bankName,
            accountNumber = account ?: "",
            amount = amount,
            balance = balance,
            content = content,
            senderName = sender,
            referenceNumber = ref,
            transactionDate = date,
            rawMessage = text,
            accountType = config.accountType
        )
    }

    private fun extractAmount(t: String): Double? {
        val p = Regex("""([+-]?[\d.,]+)\s*(?:VN[DĐ]|đ|₫)""", RegexOption.IGNORE_CASE)
        return p.find(t)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractAccount(t: String): String? {
        val p = Regex("""t[aà]i[_\s]?kho[aả]n[:\s]*(\d{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)
    }

    private fun extractContent(t: String): String? {
        val p = Regex("""n[oô]i[_\s]?dung[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)?.trim()
    }

    private fun extractSender(t: String): String? {
        val p = Regex("""(\p{Lu}+(?:\s+\p{Lu}+){1,5})\s*(?:chuyển|chuyen|ck|thanh toán|thanh toan)""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)
    }

    private fun extractBalance(t: String): Double? {
        val p = Regex("""(?:SD|số dư|so du|số dư|vi?́)[:\s]*([+-]?[\d.,]+)\s*(?:VN[DĐ]|đ|₫)""", RegexOption.IGNORE_CASE)
        return p.find(t)?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractReference(t: String): String? {
        val p = Regex("""(?:GD|giao dịch|giao dich|MGD)[:\s]*([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(t)?.groupValues?.get(1)
    }

    private fun extractDate(t: String): Long {
        val p = Regex("""(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})\s*(\d{1,2})[:\s](\d{2})""")
        val m = p.find(t)
        if (m != null) try {
            val cal = Calendar.getInstance()
            cal.set(m.groupValues[3].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())
            return cal.timeInMillis
        } catch (e: Exception) { android.util.Log.w("BaseBankParser", "date parse error", e) }
        return System.currentTimeMillis()
    }

    private fun parseAmount(s: String): Double? {
        val trimmed = s.trim()
        val sign = if (trimmed.startsWith('-')) -1.0 else 1.0
        val num = trimmed.replace(Regex("[^\\d.,]"), "")
        if (num.isEmpty()) return null
        val lastDot = num.lastIndexOf('.')
        val lastComma = num.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) num.replace(",", "")
                else num.replace(".", "").replace(",", ".")
            lastDot >= 0 -> {
                val parts = num.split(".")
                if (parts.size > 2 || parts.last().length == 3) num.replace(".", "") else num
            }
            lastComma >= 0 -> {
                val parts = num.split(",")
                if (parts.size > 2 || parts.last().length == 3) num.replace(",", "") else num.replace(",", ".")
            }
            else -> num
        }
        return normalized.toDoubleOrNull()?.let { it * sign }
    }
}
