package com.banknotify.parser

import com.banknotify.model.Transaction
import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern

class VietcombankParser : BankParser {
    override val bankCode = "VCB"
    override val bankName = "Vietcombank"
    override val packageNames = listOf(
        "com.vietcombank",
        "com.vietcombank.vcb",
        "vcb.app.com.vcb"
    )

    override fun parse(title: String, body: String): Transaction? {
        if (!title.contains("VCB", ignoreCase = true) && !title.contains("Vietcombank", ignoreCase = true)
            && !body.contains("VCB", ignoreCase = true) && !body.contains("Vietcombank", ignoreCase = true)) {
            return null
        }

        val amount = extractAmount(body) ?: return null
        val accountNumber = extractAccountNumber(body)
        val content = extractContent(body) ?: ""
        val senderName = extractSenderName(body)
        val balance = extractBalance(body)
        val referenceNumber = extractReference(body)
        val transactionDate = extractDate(body)

        return Transaction(
            bankCode = bankCode,
            bankName = bankName,
            accountNumber = accountNumber ?: "",
            amount = amount,
            balance = balance,
            content = content,
            senderName = senderName,
            referenceNumber = referenceNumber,
            transactionDate = transactionDate,
            rawMessage = "$title $body"
        )
    }

    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Regex("""([+-]?[\d,]+(?:\.\d+)?)\s*VND""", RegexOption.IGNORE_CASE),
            Regex("""số[:_]tiền[:\s]*([+-]?[\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""([+-]?[\d,]+)\s*VNĐ""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) {
                return parseAmount(m.groupValues[1])
            }
        }
        return null
    }

    private fun extractAccountNumber(text: String): String? {
        val patterns = listOf(
            Regex("""tài[_\s]?khoản[:\s]*(\d{6,20})""", RegexOption.IGNORE_CASE),
            Regex("""đến[_\s]?tk[:\s]*(\d{6,20})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{6,20})\s*(?:nhận|nhận được)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) return m.groupValues[1]
        }

        val allNumbers = Regex("""\d{8,20}""").findAll(text).toList()
        if (allNumbers.isNotEmpty()) {
            val excludePatterns = listOf(
                Regex("""0[1-9]\d{6,}"""),  // not phone numbers starting with 0
                Regex("""\d{10}""")  // skip pure 10-digit numbers (likely phone)
            )
            for (num in allNumbers) {
                val v = num.value
                if (excludePatterns.any { it.matches(v) }) continue
                return v
            }
        }
        return null
    }

    private fun extractContent(text: String): String? {
        val patterns = listOf(
            Regex("""nội[_\s]?dung[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE),
            Regex("""nd[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE),
            Regex("""ct[:\s]*([^\n]{1,200})""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) {
                val content = m.groupValues[1].trim()
                if (content.length > 3) return content
            }
        }
        return null
    }

    private fun extractSenderName(text: String): String? {
        val patterns = listOf(
            Regex("""từ\s+(\d{6,20})\s*[-–]\s*([A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+)*)""", RegexOption.IGNORE_CASE),
            Regex("""ct\s+từ\s+(\d{6,20})\s+([A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+)*)""", RegexOption.IGNORE_CASE),
            Regex("""([A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+(?:\s+[A-ZÀ-Ỹ][A-ZÀ-Ỹa-zà-ỹ]+){1,5})\s*(?:chuyển|ck|thanh toán)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) {
                val name = m.groupValues.last().trim()
                if (name.length > 3 && !name.contains("VND", ignoreCase = true)) return name
            }
        }
        return null
    }

    private fun extractBalance(text: String): Double? {
        val p = Regex("""(?:SD|số dư|so du)[:\s]*([+-]?[\d,]+(?:\.\d+)?)\s*VND""", RegexOption.IGNORE_CASE)
        val m = p.find(text)
        return m?.let { parseAmount(it.groupValues[1]) }
    }

    private fun extractReference(text: String): String? {
        val p = Regex("""(?:GD|giao dịch|MGD)[:\s]*([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)
        return p.find(text)?.groupValues?.get(1)
    }

    private fun extractDate(text: String): Long {
        val p = Regex("""(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})\s*(\d{1,2})[:\s](\d{2})""")
        val m = p.find(text)
        if (m != null) {
            try {
                val cal = java.util.Calendar.getInstance()
                cal.set(m.groupValues[3].toInt(), m.groupValues[2].toInt() - 1,
                    m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())
                return cal.timeInMillis
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    private fun parseAmount(s: String): Double {
        return s.replace("[^\\d.,]".toRegex())
            .replace(",", "")
            .toDoubleOrNull() ?: 0.0
    }
}
