package com.banknotify.service.server

import android.content.Context
import android.util.Log
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.model.TransactionFilter
import com.banknotify.core.model.TransactionStatus
import com.banknotify.service.webhook.WebhookManager
import com.banknotify.update.UpdateManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

class ApiServer(port: Int) : NanoHTTPD("0.0.0.0", port) {

    private val gson = GsonBuilder().create()
    private val TAG = "ApiServer"

    companion object {
        private const val RATE_MAX = 100
        private const val RATE_WINDOW_MS = 60000L
        private const val KEY_API_KEY = "server_api_key"
        private const val KEY_AUTH_ENABLED = "server_auth_enabled"
    }

    private val rateMap = mutableMapOf<String, RateEntry>()

    init {
        setTempFileManagerFactory(NoTempFilesManagerFactory())
    }

    private data class RateEntry(var count: Int, val windowStart: Long)

    private fun checkRate(ip: String): Boolean {
        pruneRateMap()
        val now = System.currentTimeMillis()
        val entry = rateMap.getOrPut(ip) { RateEntry(0, now) }
        if (now - entry.windowStart > RATE_WINDOW_MS) {
            entry.count = 0
            entry.windowStart = now
        }
        entry.count++
        return entry.count <= RATE_MAX
    }

    private fun pruneRateMap() {
        val cutoff = System.currentTimeMillis() - RATE_WINDOW_MS
        rateMap.entries.removeIf { it.value.windowStart < cutoff }
    }

    private fun checkAuth(headers: Map<String, String>): Boolean {
        val prefs = BankNotifyApp.instance.getSharedPreferences(BankNotifyApp.PREF_SERVER, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_AUTH_ENABLED, false)) return true
        val key = prefs.getString(KEY_API_KEY, "") ?: ""
        if (key.isBlank()) return true
        return headers["x-api-key"] == key || headers["authorization"] == "Bearer $key"
    }

    override fun serve(session: IHTTPSession): Response {
        try {
            val method = session.method
            val uri = session.uri.trimEnd('/')
            val ip = session.remoteIpAddress

            if (!checkRate(ip)) {
                return json(mapOf("success" to false, "error" to "Rate limit exceeded"), 429)
            }
            if (!checkAuth(session.headers)) {
                return json(mapOf("success" to false, "error" to "Unauthorized"), 401)
            }
            if (method == Method.OPTIONS) {
                return json(mapOf("success" to true), 200).also {
                    it.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
                    it.addHeader("Access-Control-Allow-Headers", "Content-Type, X-API-Key, Authorization")
                }
            }

            val v1 = "/api/v1"
            val base = when {
                uri.startsWith(v1) -> uri.removePrefix(v1)
                uri.startsWith("/api") -> uri.removePrefix("/api")
                else -> uri
            }

            return when {
                base == "/health" && method == Method.GET -> ok("status" to "ok", "version" to BankNotifyApp.instance.appVersion)

                base == "/transactions" && method == Method.GET -> handleListTransactions(session)
                base == "/transactions/recent" && method == Method.GET -> handleRecent(session)
                base == "/transactions/unread" && method == Method.GET -> handleUnread()
                base == "/transactions/stats" && method == Method.GET -> handleStats()

                base.matches(Regex("/transactions/\\d+")) && method == Method.GET -> handleGetTx(base.split("/").last().toLongOrNull())
                base.matches(Regex("/transactions/\\d+/confirm")) && method == Method.POST -> handleConfirmTx(base.split("/").dropLast(1).last().toLongOrNull())
                base.matches(Regex("/transactions/\\d+")) && method == Method.DELETE -> handleDeleteTx(base.split("/").last().toLongOrNull())

                base == "/webhook" && method == Method.GET -> handleGetWebhook()
                base == "/webhook" && method == Method.POST -> handleSetWebhook(session)
                base == "/webhook/test" && method == Method.POST -> handleTestWebhook(session)

                base == "/update/check" && method == Method.GET -> handleCheckUpdate()
                base == "/update/check" && method == Method.POST -> handleCheckUpdate()
                base == "/update/info" && method == Method.GET -> handleUpdateInfo()
                base == "/update/url" && method == Method.GET -> handleGetUpdateUrl()
                base == "/update/url" && method == Method.POST -> handleSetUpdateUrl(session)

                base == "/config" && method == Method.GET -> handleGetConfig()
                base == "/config" && method == Method.POST -> handleSetConfig(session)

                else -> error(404, "Endpoint not found: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "serve error", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal error")
        }
    }

    private fun handleListTransactions(session: IHTTPSession): Response {
        val p = session.parms ?: emptyMap()
        val filter = TransactionFilter(
            bankCode = p["bank_code"],
            status = p["status"]?.let { try { TransactionStatus.valueOf(it.uppercase()) } catch (e: Exception) { Log.w(TAG, "Invalid status: ${it}"); null } },
            fromDate = p["from_date"]?.toLongOrNull(),
            toDate = p["to_date"]?.toLongOrNull(),
            minAmount = p["min_amount"]?.toDoubleOrNull(),
            maxAmount = p["max_amount"]?.toDoubleOrNull(),
            searchContent = p["search"],
            limit = (p["limit"]?.toIntOrNull())?.coerceIn(1, 200) ?: 50,
            offset = (p["offset"]?.toIntOrNull())?.coerceAtLeast(0) ?: 0
        )
        val txs = BankNotifyApp.instance.dbHelper.getTransactions(filter)
        return ok("success" to true, "data" to txs, "total" to txs.size)
    }

    private fun handleRecent(session: IHTTPSession): Response {
        val p = session.parms ?: emptyMap()
        val limit = (p["limit"]?.toIntOrNull())?.coerceIn(1, 100) ?: 20
        val offset = (p["offset"]?.toIntOrNull())?.coerceAtLeast(0) ?: 0
        val txs = BankNotifyApp.instance.dbHelper.getRecentTransactions(limit, offset)
        return ok("success" to true, "data" to txs)
    }

    private fun handleUnread() = ok("success" to true, "count" to BankNotifyApp.instance.dbHelper.getUnreadCount())

    private fun handleStats(): Response {
        val db = BankNotifyApp.instance.dbHelper
        return ok("success" to true, "data" to mapOf(
            "total_transactions" to db.getTotalTransactions(),
            "total_amount" to db.getTotalAmount(),
            "unread_count" to db.getUnreadCount()
        ))
    }

    private fun handleGetTx(id: Long?): Response {
        if (id == null) return error(400, "Invalid ID")
        val tx = BankNotifyApp.instance.dbHelper.getTransaction(id)
        return if (tx != null) ok("success" to true, "data" to tx) else error(404, "Not found")
    }

    private fun handleConfirmTx(id: Long?): Response {
        if (id == null) return error(400, "Invalid ID")
        val db = BankNotifyApp.instance.dbHelper
        return if (db.getTransaction(id) != null) {
            db.updateStatus(id, TransactionStatus.CONFIRMED)
            ok("success" to true, "message" to "Confirmed")
        } else error(404, "Not found")
    }

    private fun handleDeleteTx(id: Long?): Response {
        if (id == null) return error(400, "Invalid ID")
        BankNotifyApp.instance.dbHelper.deleteTransaction(id)
        return ok("success" to true, "message" to "Deleted")
    }

    private fun handleGetWebhook(): Response = ok("success" to true, "data" to mapOf(
        "url" to WebhookManager.webhookUrl, "enabled" to WebhookManager.isEnabled, "retry_count" to WebhookManager.retryCount
    ))

    private fun handleSetWebhook(session: IHTTPSession): Response {
        val body = readBody(session) ?: return error(400, "Empty body")
        try {
            val data = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return error(400, "Invalid JSON")
            (data["url"] as? String)?.let { if (it.isNotBlank() && !it.startsWith("http")) return error(400, "Invalid URL") }
            (data["url"] as? String)?.let { WebhookManager.webhookUrl = it }
            (data["enabled"] as? Boolean)?.let { WebhookManager.isEnabled = it }
            (data["retry_count"] as? Number)?.let { WebhookManager.retryCount = it.toInt() }
            (data["secret"] as? String)?.let { WebhookManager.secret = it }
            return ok("success" to true, "message" to "Webhook updated")
        } catch (e: Exception) {
            return error(400, e.message ?: "Invalid request")
        }
    }

    private fun handleTestWebhook(session: IHTTPSession): Response {
        val body = readBody(session) ?: return error(400, "Empty body")
        val url = try { (gson.fromJson(body, Map::class.java) as? Map<*, *>)?.get("url") as? String } catch (e: Exception) { Log.e(TAG, "parse test webhook url", e); null }
        if (url.isNullOrBlank()) return error(400, "Missing url")
        WebhookManager.testWebhook(url) { success, msg -> Log.i(TAG, "Webhook test: $success $msg") }
        return ok("success" to true, "message" to "Test dispatched")
    }

    private fun handleCheckUpdate(): Response {
        val app = BankNotifyApp.instance
        val info = mapOf(
            "has_update" to false,
            "current_version" to app.appVersion,
            "current_build" to app.appBuild,
            "check_url" to UpdateManager.checkUrl,
            "check_url_configured" to UpdateManager.checkUrl.isNotBlank()
        )
        return ok("success" to true, "data" to info)
    }

    private fun handleUpdateInfo(): Response {
        val url = UpdateManager.checkUrl
        return ok("success" to true, "data" to mapOf(
            "check_url" to url,
            "configured" to url.isNotBlank(),
            "current_version" to BankNotifyApp.instance.appVersion,
            "current_build" to BankNotifyApp.instance.appBuild
        ))
    }

    private fun handleGetUpdateUrl() = ok("success" to true, "url" to UpdateManager.checkUrl)

    private fun handleSetUpdateUrl(session: IHTTPSession): Response {
        val body = readBody(session) ?: return error(400, "Empty body")
        try {
            val data = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return error(400, "Invalid JSON")
            (data["url"] as? String)?.let { UpdateManager.setUpdateCheckUrl(it) }
            return ok("success" to true, "message" to "Update URL updated")
        } catch (e: Exception) {
            return error(400, e.message ?: "Invalid request")
        }
    }

    private fun handleGetConfig(): Response {
        val prefs = BankNotifyApp.instance.getSharedPreferences(BankNotifyApp.PREF_SERVER, Context.MODE_PRIVATE)
        return ok("success" to true, "data" to mapOf(
            "server_port" to listeningPort,
            "auth_enabled" to prefs.getBoolean(KEY_AUTH_ENABLED, false),
            "webhook_url" to WebhookManager.webhookUrl,
            "webhook_enabled" to WebhookManager.isEnabled,
            "update_url" to UpdateManager.checkUrl
        ))
    }

    private fun handleSetConfig(session: IHTTPSession): Response {
        val body = readBody(session) ?: return error(400, "Empty body")
        try {
            val data = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return error(400, "Invalid JSON")
            val prefs = BankNotifyApp.instance.getSharedPreferences(BankNotifyApp.PREF_SERVER, Context.MODE_PRIVATE)

            (data["auth_enabled"] as? Boolean)?.let { prefs.edit().putBoolean(KEY_AUTH_ENABLED, it).apply() }
            (data["api_key"] as? String)?.let { prefs.edit().putString(KEY_API_KEY, it.trim()).apply() }
            (data["server_port"] as? Number)?.let { newPort ->
                val p = newPort.toInt()
                if (p in 1024..65535 && p != listeningPort) {
                    ApiServerService.restart(p)
                }
            }
            (data["update_url"] as? String)?.let { UpdateManager.setUpdateCheckUrl(it) }

            return ok("success" to true, "message" to "Config updated")
        } catch (e: Exception) {
            return error(400, e.message ?: "Invalid request")
        }
    }

    private fun ok(vararg pairs: Pair<String, Any>): Response = json(mapOf(*pairs), 200)
    private fun error(code: Int, msg: String): Response = json(mapOf("success" to false, "error" to msg), code)

    private fun json(data: Any, status: Int): Response {
        val json = gson.toJson(data)
        val resp = newFixedLengthResponse(Response.Status.lookup(status), "application/json", json)
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, X-API-Key, Authorization")
        return resp
    }

    private fun readBody(session: IHTTPSession): String? {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            files["postData"]
        } catch (e: Exception) { Log.e(TAG, "readBody error", e); null }
    }

    override fun serveFixedSize(session: IHTTPSession, mimeType: String, file: java.io.InputStream?, filesize: Long): Response? = null
    override fun serveFile(session: IHTTPSession, files: MutableMap<String, String>, mimeType: String, file: java.io.File?, mimeTypes: MutableMap<String, String>?): Response? = null

    private class NoTempFilesManager : TempFileManager {
        private val files = mutableListOf<NoTempFile>()
        override fun createTempFile(hint: String?): TempFile { val f = NoTempFile(); files.add(f); return f }
        override fun clear() { files.clear() }
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
