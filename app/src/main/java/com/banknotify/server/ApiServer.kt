package com.banknotify.server

import android.util.Log
import com.banknotify.BankNotifyApp
import com.banknotify.model.TransactionFilter
import com.banknotify.model.TransactionStatus
import com.banknotify.webhook.WebhookManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

class ApiServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val TAG = "ApiServer"

    init {
        setTempFileManagerFactory(NoTempFilesManagerFactory())
    }

    override fun serve(session: IHTTPSession): Response {
        try {
            val method = session.method
            val uri = session.uri.trimEnd('/')
            val params = session.parms ?: mutableMapOf()

            Log.d(TAG, "$method $uri")

            return when {
                uri == "/api/health" -> jsonResponse(mapOf("status" to "ok", "timestamp" to System.currentTimeMillis()))

                uri == "/api/transactions" && method == Method.GET -> handleGetTransactions(params)
                uri == "/api/transactions/recent" && method == Method.GET -> handleGetRecent(params)
                uri == "/api/transactions/unread" && method == Method.GET -> handleGetUnreadCount()

                uri.matches(Regex("/api/transactions/\\d+")) && method == Method.GET -> {
                    val id = uri.split("/").last().toLongOrNull()
                    if (id != null) handleGetTransaction(id) else notFound("Invalid ID")
                }

                uri.matches(Regex("/api/transactions/\\d+/confirm")) && method == Method.POST -> {
                    val id = uri.split("/").last().toLongOrNull()
                    if (id != null) handleConfirmTransaction(id) else notFound("Invalid ID")
                }

                uri.matches(Regex("/api/transactions/\\d+")) && method == Method.DELETE -> {
                    val id = uri.split("/").last().toLongOrNull()
                    if (id != null) handleDeleteTransaction(id) else notFound("Invalid ID")
                }

                uri == "/api/transactions/stats" && method == Method.GET -> handleGetStats()

                uri == "/api/webhook" && method == Method.GET -> handleGetWebhook()
                uri == "/api/webhook/test" && method == Method.POST -> handleTestWebhook(session)
                uri == "/api/webhook" && method == Method.POST -> handleUpdateWebhook(session)

                uri == "/api/config" && method == Method.GET -> handleGetConfig()
                uri == "/api/config" && method == Method.POST -> handleUpdateConfig(session)

                else -> notFound("Endpoint not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Internal error: ${e.message}"
            )
        }
    }

    private fun handleGetTransactions(params: Map<String, String>): Response {
        val filter = TransactionFilter(
            bankCode = params["bank_code"],
            status = params["status"]?.let { try { TransactionStatus.valueOf(it.uppercase()) } catch (_: Exception) { null } },
            fromDate = params["from_date"]?.toLongOrNull(),
            toDate = params["to_date"]?.toLongOrNull(),
            minAmount = params["min_amount"]?.toDoubleOrNull(),
            maxAmount = params["max_amount"]?.toDoubleOrNull(),
            searchContent = params["search"],
            limit = params["limit"]?.toIntOrNull() ?: 50,
            offset = params["offset"]?.toIntOrNull() ?: 0
        )
        val transactions = BankNotifyApp.instance.dbHelper.getTransactions(filter)
        return jsonResponse(mapOf(
            "success" to true,
            "data" to transactions,
            "total" to transactions.size,
            "limit" to filter.limit,
            "offset" to filter.offset
        ))
    }

    private fun handleGetRecent(params: Map<String, String>): Response {
        val limit = params["limit"]?.toIntOrNull() ?: 20
        val offset = params["offset"]?.toIntOrNull() ?: 0
        val transactions = BankNotifyApp.instance.dbHelper.getRecentTransactions(limit, offset)
        return jsonResponse(mapOf("success" to true, "data" to transactions))
    }

    private fun handleGetTransaction(id: Long): Response {
        val tx = BankNotifyApp.instance.dbHelper.getTransaction(id)
        return if (tx != null) {
            jsonResponse(mapOf("success" to true, "data" to tx))
        } else {
            notFound("Transaction not found")
        }
    }

    private fun handleConfirmTransaction(id: Long): Response {
        val db = BankNotifyApp.instance.dbHelper
        val tx = db.getTransaction(id)
        if (tx == null) return notFound("Transaction not found")
        db.updateStatus(id, TransactionStatus.CONFIRMED)
        return jsonResponse(mapOf("success" to true, "message" to "Transaction confirmed"))
    }

    private fun handleDeleteTransaction(id: Long): Response {
        BankNotifyApp.instance.dbHelper.deleteTransaction(id)
        return jsonResponse(mapOf("success" to true, "message" to "Transaction deleted"))
    }

    private fun handleGetUnreadCount(): Response {
        val count = BankNotifyApp.instance.dbHelper.getUnreadCount()
        return jsonResponse(mapOf("success" to true, "count" to count))
    }

    private fun handleGetStats(): Response {
        val db = BankNotifyApp.instance.dbHelper
        return jsonResponse(mapOf(
            "success" to true,
            "data" to mapOf(
                "total_transactions" to db.getTotalTransactions(),
                "total_amount" to db.getTotalAmount(),
                "unread_count" to db.getUnreadCount()
            )
        ))
    }

    private fun handleGetWebhook(): Response {
        return jsonResponse(mapOf(
            "success" to true,
            "data" to mapOf(
                "url" to WebhookManager.webhookUrl,
                "enabled" to WebhookManager.isEnabled,
                "retry_count" to WebhookManager.retryCount
            )
        ))
    }

    private fun handleTestWebhook(session: IHTTPSession): Response {
        val body = readBody(session)
        val url = try {
            gson.fromJson(body, Map::class.java)?.get("url") as? String
        } catch (_: Exception) { null }
        if (url.isNullOrBlank()) {
            return jsonResponse(mapOf("success" to false, "error" to "Missing url"), Response.Status.BAD_REQUEST)
        }
        WebhookManager.testWebhook(url) { success, message ->
            Log.i(TAG, "Webhook test: $success $message")
        }
        return jsonResponse(mapOf("success" to true, "message" to "Test webhook dispatched"))
    }

    private fun handleUpdateWebhook(session: IHTTPSession): Response {
        val body = readBody(session)
        try {
            val data = gson.fromJson(body, Map::class.java) as? Map<*, *>
            data?.let {
                (it["url"] as? String)?.let { v -> WebhookManager.webhookUrl = v }
                (it["enabled"] as? Boolean)?.let { v -> WebhookManager.isEnabled = v }
                (it["retry_count"] as? Number)?.let { v -> WebhookManager.retryCount = v.toInt() }
                (it["secret"] as? String)?.let { v -> WebhookManager.secret = v }
            }
            return jsonResponse(mapOf("success" to true, "message" to "Webhook updated"))
        } catch (e: Exception) {
            return jsonResponse(mapOf("success" to false, "error" to e.message), Response.Status.BAD_REQUEST)
        }
    }

    private fun handleGetConfig(): Response {
        return jsonResponse(mapOf(
            "success" to true,
            "data" to mapOf(
                "server_port" to listeningPort,
                "webhook_url" to WebhookManager.webhookUrl,
                "webhook_enabled" to WebhookManager.isEnabled,
                "webhook_retry" to WebhookManager.retryCount
            )
        ))
    }

    private fun handleUpdateConfig(session: IHTTPSession): Response {
        val body = readBody(session)
        try {
            val data = gson.fromJson(body, Map::class.java) as? Map<*, *>
            data?.let {
                (it["server_port"] as? Number)?.let { v ->
                    val newPort = v.toInt()
                    if (newPort in 1024..65535 && newPort != listeningPort) {
                        ApiServerService.restart(newPort)
                    }
                }
            }
            return jsonResponse(mapOf("success" to true, "message" to "Config updated"))
        } catch (e: Exception) {
            return jsonResponse(mapOf("success" to false, "error" to e.message), Response.Status.BAD_REQUEST)
        }
    }

    private fun jsonResponse(data: Any, status: Response.Status = Response.Status.OK): Response {
        val json = gson.toJson(data)
        val response = newFixedLengthResponse(status, "application/json", json)
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }

    private fun notFound(message: String): Response {
        return jsonResponse(
            mapOf("success" to false, "error" to message),
            Response.Status.NOT_FOUND
        )
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files.getOrDefault("postData", "")
    }

    override fun serveFixedSize(
        session: IHTTPSession,
        mimeType: String,
        file: java.io.InputStream?,
        filesize: Long
    ): Response? {
        Log.e(TAG, "serveFixedSize should not be called")
        return null
    }

    override fun serveFile(
        session: IHTTPSession,
        files: MutableMap<String, String>,
        mimeType: String,
        file: java.io.File?,
        mimeTypes: MutableMap<String, String>?
    ): Response? {
        Log.e(TAG, "serveFile should not be called")
        return null
    }

    private class NoTempFilesManager : TempFileManager {
        private val files = mutableListOf<NoTempFile>()
        override fun createTempFile(filename_hint: String?): TempFile {
            val f = NoTempFile()
            files.add(f)
            return f
        }
        override fun clear() {
            files.forEach { it.delete() }
            files.clear()
        }
    }

    private class NoTempFile : TempFile {
        private val data = java.io.ByteArrayOutputStream()
        override fun open() = ByteArrayInputStream(data.toByteArray())
        override fun getName() = ""
        override fun getOutputStream() = data
        override fun delete() {}
    }

    private class NoTempFilesManagerFactory : TempFileManagerFactory {
        override fun create() = NoTempFilesManager()
    }
}
