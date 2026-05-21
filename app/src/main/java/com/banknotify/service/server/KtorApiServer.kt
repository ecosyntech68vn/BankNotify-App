package com.banknotify.service.server

import android.util.Log
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.SecurePrefs
import com.banknotify.core.model.TransactionFilter
import com.banknotify.core.model.TransactionStatus
import com.banknotify.service.webhook.WebhookManager
import com.banknotify.update.UpdateManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.security.MessageDigest

object KtorApiServer {

    private data class RateEntry(var count: Int, val windowStart: Long)

    private const val RATE_MAX = 100
    private const val RATE_WINDOW_MS = 60000L
    private const val TAG = "KtorApiServer"

    private var server: ApplicationEngine? = null
    private val rateMap = mutableMapOf<String, RateEntry>()
    private val gson = GsonBuilder().create()

    @Volatile
    var port: Int = BankNotifyApp.DEFAULT_PORT
        private set

    val isRunning: Boolean get() = server != null

    fun start(desiredPort: Int) {
        if (server != null) return
        port = desiredPort
        server = embeddedServer(CIO, port = desiredPort, module = Application::module).start(wait = false)
        Log.i(TAG, "Server started on port $desiredPort")
    }

    fun stop() {
        server?.stop(1000, 3000)
        rateMap.clear()
        server = null
        Log.i(TAG, "Server stopped")
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            gson { setPrettyPrinting() }
        }

        intercept(ApplicationCallPipeline.Call) {
            val ip = call.request.origin.remoteHost
            if (!checkRate(ip)) {
                call.respond(HttpStatusCode(429, "Too Many Requests"), mapOf("success" to false, "error" to "Rate limit exceeded"))
                return@intercept
            }
            if (call.request.httpMethod == HttpMethod.Options) {
                call.respond(mapOf("success" to true))
                return@intercept
            }
            if (!checkAuth(call.request)) {
                call.respond(HttpStatusCode(401, "Unauthorized"), mapOf("success" to false, "error" to "Unauthorized"))
                return@intercept
            }
        }

        routing {
            v1Routes("/api/v1")
            v1Routes("/api")
        }
    }

    private fun Routing.v1Routes(prefix: String) {
        get("$prefix/health") {
            call.respond(mapOf("success" to true, "status" to "ok", "version" to BankNotifyApp.instance.appVersion))
        }

        get("$prefix/transactions") {
            val p = call.request.queryParameters
            val filter = TransactionFilter(
                bankCode = p["bank_code"],
                status = p["status"]?.let { try { TransactionStatus.valueOf(it.uppercase()) } catch (_: Exception) { null } },
                fromDate = p["from_date"]?.toLongOrNull(),
                toDate = p["to_date"]?.toLongOrNull(),
                minAmount = p["min_amount"]?.toDoubleOrNull(),
                maxAmount = p["max_amount"]?.toDoubleOrNull(),
                searchContent = p["search"],
                limit = p["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50,
                offset = p["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            )
            val txs = BankNotifyApp.instance.dbHelper.getTransactions(filter)
            call.respond(mapOf("success" to true, "data" to txs, "total" to txs.size))
        }

        get("$prefix/transactions/recent") {
            val p = call.request.queryParameters
            val limit = p["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val offset = p["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            call.respond(mapOf("success" to true, "data" to BankNotifyApp.instance.dbHelper.getRecentTransactions(limit, offset)))
        }

        get("$prefix/transactions/unread") {
            call.respond(mapOf("success" to true, "count" to BankNotifyApp.instance.dbHelper.getUnreadCount()))
        }

        get("$prefix/transactions/stats") {
            val db = BankNotifyApp.instance.dbHelper
            call.respond(mapOf("success" to true, "data" to mapOf(
                "total_transactions" to db.getTotalTransactions(),
                "total_amount" to db.getTotalAmount(),
                "unread_count" to db.getUnreadCount()
            )))
        }

        get("$prefix/transactions/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid ID"))
                return@get
            }
            val tx = BankNotifyApp.instance.dbHelper.getTransaction(id)
            if (tx != null) call.respond(mapOf("success" to true, "data" to tx))
            else call.respond(HttpStatusCode(404, "Not Found"), mapOf("success" to false, "error" to "Not found"))
        }

        post("$prefix/transactions/{id}/confirm") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid ID"))
                return@post
            }
            val db = BankNotifyApp.instance.dbHelper
            if (db.getTransaction(id) != null) {
                db.updateStatus(id, TransactionStatus.CONFIRMED)
                call.respond(mapOf("success" to true, "message" to "Confirmed"))
            } else {
                call.respond(HttpStatusCode(404, "Not Found"), mapOf("success" to false, "error" to "Not found"))
            }
        }

        delete("$prefix/transactions/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid ID"))
                return@delete
            }
            BankNotifyApp.instance.dbHelper.deleteTransaction(id)
            call.respond(mapOf("success" to true, "message" to "Deleted"))
        }

        get("$prefix/webhook") {
            call.respond(mapOf("success" to true, "data" to mapOf(
                "url" to WebhookManager.webhookUrl,
                "enabled" to WebhookManager.isEnabled,
                "retry_count" to WebhookManager.retryCount
            )))
        }

        post("$prefix/webhook") {
            val body = call.receiveText()
            if (body.isBlank()) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Empty body"))
                return@post
            }
            try {
                val data = gson.fromJson(body, Map::class.java) as? Map<*, *>
                if (data == null) {
                    call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid JSON"))
                    return@post
                }
                (data["url"] as? String)?.let { url ->
                    if (url.isNotBlank() && !url.startsWith("http")) {
                        call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid URL"))
                        return@post
                    }
                    WebhookManager.webhookUrl = url
                }
                (data["enabled"] as? Boolean)?.let { WebhookManager.isEnabled = it }
                (data["retry_count"] as? Number)?.let { WebhookManager.retryCount = it.toInt() }
                (data["secret"] as? String)?.let { WebhookManager.secret = it }
                call.respond(mapOf("success" to true, "message" to "Webhook updated"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to (e.message ?: "Invalid request")))
            }
        }

        post("$prefix/webhook/test") {
            val body = call.receiveText()
            val url = try { (gson.fromJson(body, Map::class.java) as? Map<*, *>)?.get("url") as? String } catch (_: Exception) { null }
            if (url.isNullOrBlank()) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Missing url"))
                return@post
            }
            WebhookManager.testWebhook(url) { success, msg -> Log.i(TAG, "Webhook test: $success $msg") }
            call.respond(mapOf("success" to true, "message" to "Test dispatched"))
        }

        get("$prefix/update/check") {
            val app = BankNotifyApp.instance
            call.respond(mapOf("success" to true, "data" to mapOf(
                "has_update" to false,
                "current_version" to app.appVersion,
                "current_build" to app.appBuild,
                "check_url" to UpdateManager.checkUrl,
                "check_url_configured" to UpdateManager.checkUrl.isNotBlank()
            )))
        }

        post("$prefix/update/check") {
            val app = BankNotifyApp.instance
            call.respond(mapOf("success" to true, "data" to mapOf(
                "has_update" to false,
                "current_version" to app.appVersion,
                "current_build" to app.appBuild,
                "check_url" to UpdateManager.checkUrl,
                "check_url_configured" to UpdateManager.checkUrl.isNotBlank()
            )))
        }

        get("$prefix/update/info") {
            val url = UpdateManager.checkUrl
            call.respond(mapOf("success" to true, "data" to mapOf(
                "check_url" to url,
                "configured" to url.isNotBlank(),
                "current_version" to BankNotifyApp.instance.appVersion,
                "current_build" to BankNotifyApp.instance.appBuild
            )))
        }

        get("$prefix/update/url") {
            call.respond(mapOf("success" to true, "url" to UpdateManager.checkUrl))
        }

        post("$prefix/update/url") {
            val body = call.receiveText()
            try {
                val data = gson.fromJson(body, Map::class.java) as? Map<*, *>
                if (data == null) {
                    call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid JSON"))
                    return@post
                }
                (data["url"] as? String)?.let { UpdateManager.setUpdateCheckUrl(it) }
                call.respond(mapOf("success" to true, "message" to "Update URL updated"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to (e.message ?: "Invalid request")))
            }
        }

        get("$prefix/config") {
            val ctx = BankNotifyApp.instance
            call.respond(mapOf("success" to true, "data" to mapOf(
                "server_port" to port,
                "auth_enabled" to SecurePrefs.getBool(ctx, BankNotifyApp.PREF_SERVER, "server_auth_enabled"),
                "webhook_url" to WebhookManager.webhookUrl,
                "webhook_enabled" to WebhookManager.isEnabled,
                "update_url" to UpdateManager.checkUrl
            )))
        }

        post("$prefix/config") {
            val body = call.receiveText()
            try {
                val data = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: run {
                    call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid JSON"))
                    return@post
                }
                val ctx = BankNotifyApp.instance
                (data["auth_enabled"] as? Boolean)?.let { SecurePrefs.setBool(ctx, BankNotifyApp.PREF_SERVER, "server_auth_enabled", it) }
                (data["api_key"] as? String)?.let { SecurePrefs.setString(ctx, BankNotifyApp.PREF_SERVER, "server_api_key", it.trim()) }
                (data["server_port"] as? Number)?.let { newPort ->
                    val p = newPort.toInt()
                    if (p in 1024..65535 && p != port) {
                        ApiServerService.restart(p)
                    }
                }
                (data["update_url"] as? String)?.let { UpdateManager.setUpdateCheckUrl(it) }
                call.respond(mapOf("success" to true, "message" to "Config updated"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to (e.message ?: "Invalid request")))
            }
        }
    }

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

    private fun checkAuth(request: io.ktor.server.request.ApplicationRequest): Boolean {
        val ctx = BankNotifyApp.instance
        if (!SecurePrefs.getBool(ctx, BankNotifyApp.PREF_SERVER, "server_auth_enabled")) return true
        val key = SecurePrefs.getString(ctx, BankNotifyApp.PREF_SERVER, "server_api_key")
        if (key.isBlank()) return true
        val provided = request.headers["X-API-Key"] ?: request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: ""
        return MessageDigest.isEqual(key.toByteArray(), provided.toByteArray())
    }
}
