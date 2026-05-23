package com.banknotify.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.banknotify.core.db.DatabaseHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataExporter {

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    fun exportJson(context: Context, dbHelper: DatabaseHelper): File {
        val txs = dbHelper.getRecentTransactions(Int.MAX_VALUE, 0)
        val json = buildString {
            appendLine("[")
            txs.forEachIndexed { i, tx ->
                appendLine("  {")
                appendLine("    \"id\": ${tx.id},")
                appendLine("    \"bank_code\": \"${escape(tx.bankCode)}\",")
                appendLine("    \"bank_name\": \"${escape(tx.bankName)}\",")
                appendLine("    \"account_number\": \"${escape(tx.accountNumber)}\",")
                appendLine("    \"amount\": ${tx.amount},")
                appendLine("    \"balance\": ${tx.balance ?: "null"},")
                appendLine("    \"content\": \"${escape(tx.content)}\",")
                appendLine("    \"sender_name\": \"${escape(tx.senderName ?: "")}\",")
                appendLine("    \"sender_account\": \"${escape(tx.senderAccount ?: "")}\",")
                appendLine("    \"reference_number\": \"${escape(tx.referenceNumber ?: "")}\",")
                appendLine("    \"transaction_date\": ${tx.transactionDate},")
                appendLine("    \"date_text\": \"${dateFmt.format(Date(tx.transactionDate))}\",")
                appendLine("    \"status\": \"${tx.status.name}\"")
                append(if (i < txs.size - 1) "  },\n" else "  }\n")
            }
            appendLine("]")
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "transactions_export.json")
        file.writeText(json, Charsets.UTF_8)
        return file
    }

    fun exportCsv(context: Context, dbHelper: DatabaseHelper): File {
        val txs = dbHelper.getRecentTransactions(Int.MAX_VALUE, 0)
        val csv = buildString {
            appendLine("ID,Bank Code,Bank Name,Account Number,Amount,Balance,Content,Sender Name,Sender Account,Reference,Date,Status")
            txs.forEach { tx ->
                appendLine("${tx.id},${csvEscape(tx.bankCode)},${csvEscape(tx.bankName)},${csvEscape(tx.accountNumber)},${tx.amount},${tx.balance ?: ""},${csvEscape(tx.content)},${csvEscape(tx.senderName ?: "")},${csvEscape(tx.senderAccount ?: "")},${csvEscape(tx.referenceNumber ?: "")},${dateFmt.format(Date(tx.transactionDate))},${tx.status.name}")
            }
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "transactions_export.csv")
        file.writeText(csv, Charsets.UTF_8)
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "BankNotify Export"))
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun csvEscape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"${s.replace("\"", "\"\"")}\"" else s
}
