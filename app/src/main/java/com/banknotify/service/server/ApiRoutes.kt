package com.banknotify.service.server

import android.content.Context
import android.util.Log
import com.banknotify.core.AppConfig
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.SecurePrefs
import com.banknotify.core.db.DatabaseHelper
import com.banknotify.core.model.TransactionFilter
import com.banknotify.core.model.TransactionStatus
import com.banknotify.service.webhook.WebhookManager
import com.banknotify.update.UpdateManager
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

internal fun Routing.apiRoutes(
    dbHelper: DatabaseHelper,
    webhookManager: WebhookManager,
    updateManager: UpdateManager,
    appConfig: AppConfig,
    appContext: Context,
    gson: Gson,
    port: () -> Int
) {
    listOf("/api/v1", "/api").forEach { prefix -> v1Routes(prefix, dbHelper, webhookManager, updateManager, appConfig, appContext, gson, port) }
}

private fun Routing.v1Routes(
    prefix: String,
    dbHelper: DatabaseHelper,
    webhookManager: WebhookManager,
    updateManager: UpdateManager,
    appConfig: AppConfig,
    appContext: Context,
    gson: Gson,
    port: () -> Int
) {
    get("$prefix/health") {
        call.respond(mapOf("success" to true, "status" to "ok", "version" to appConfig.version))
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
        val txs = dbHelper.getTransactions(filter)
        call.respond(mapOf("success" to true, "data" to txs, "total" to txs.size))
    }

    get("$prefix/transactions/recent") {
        val p = call.request.queryParameters
        val limit = p["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val offset = p["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        call.respond(mapOf("success" to true, "data" to dbHelper.getRecentTransactions(limit, offset)))
    }

    get("$prefix/transactions/unread") {
        call.respond(mapOf("success" to true, "count" to dbHelper.getUnreadCount()))
    }

    get("$prefix/transactions/stats") {
        call.respond(mapOf("success" to true, "data" to mapOf(
            "total_transactions" to dbHelper.getTotalTransactions(),
            "total_amount" to dbHelper.getTotalAmount(),
            "unread_count" to dbHelper.getUnreadCount()
        )))
    }

    get("$prefix/transactions/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid ID"))
            return@get
        }
        val tx = dbHelper.getTransaction(id)
        if (tx != null) call.respond(mapOf("success" to true, "data" to tx))
        else call.respond(HttpStatusCode(404, "Not Found"), mapOf("success" to false, "error" to "Not found"))
    }

    post("$prefix/transactions/{id}/confirm") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid ID"))
            return@post
        }
        if (dbHelper.getTransaction(id) != null) {
            dbHelper.updateStatus(id, TransactionStatus.CONFIRMED)
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
        dbHelper.deleteTransaction(id)
        call.respond(mapOf("success" to true, "message" to "Deleted"))
    }

    get("$prefix/webhook") {
        call.respond(mapOf("success" to true, "data" to mapOf(
            "url" to webhookManager.webhookUrl,
            "enabled" to webhookManager.isEnabled,
            "retry_count" to webhookManager.retryCount
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
                webhookManager.webhookUrl = url
            }
            (data["enabled"] as? Boolean)?.let { webhookManager.isEnabled = it }
            (data["retry_count"] as? Number)?.let { webhookManager.retryCount = it.toInt() }
            (data["secret"] as? String)?.let { webhookManager.secret = it }
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
        webhookManager.testWebhook(url) { success, msg -> Log.i("ApiRoutes", "Webhook test: $success $msg") }
        call.respond(mapOf("success" to true, "message" to "Test dispatched"))
    }

    get("$prefix/update/check") {
        call.respond(mapOf("success" to true, "data" to mapOf(
            "has_update" to false,
            "current_version" to appConfig.version,
            "current_build" to appConfig.build,
            "check_url" to updateManager.checkUrl,
            "check_url_configured" to updateManager.checkUrl.isNotBlank()
        )))
    }

    post("$prefix/update/check") {
        call.respond(mapOf("success" to true, "data" to mapOf(
            "has_update" to false,
            "current_version" to appConfig.version,
            "current_build" to appConfig.build,
            "check_url" to updateManager.checkUrl,
            "check_url_configured" to updateManager.checkUrl.isNotBlank()
        )))
    }

    get("$prefix/update/info") {
        call.respond(mapOf("success" to true, "data" to mapOf(
            "check_url" to updateManager.checkUrl,
            "configured" to updateManager.checkUrl.isNotBlank(),
            "current_version" to appConfig.version,
            "current_build" to appConfig.build
        )))
    }

    get("$prefix/update/url") {
        call.respond(mapOf("success" to true, "url" to updateManager.checkUrl))
    }

    post("$prefix/update/url") {
        val body = call.receiveText()
        try {
            val data = gson.fromJson(body, Map::class.java) as? Map<*, *>
            if (data == null) {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid JSON"))
                return@post
            }
            (data["url"] as? String)?.let { updateManager.checkUrl = it }
            call.respond(mapOf("success" to true, "message" to "Update URL updated"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to (e.message ?: "Invalid request")))
        }
    }

    get("$prefix/config") {
        call.respond(mapOf("success" to true, "data" to mapOf(
            "server_port" to port(),
            "auth_enabled" to SecurePrefs.getBool(appContext, BankNotifyApp.PREF_SERVER, "server_auth_enabled"),
            "webhook_url" to webhookManager.webhookUrl,
            "webhook_enabled" to webhookManager.isEnabled,
            "update_url" to updateManager.checkUrl
        )))
    }

    post("$prefix/config") {
        val body = call.receiveText()
        try {
            val data = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: run {
                call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to "Invalid JSON"))
                return@post
            }
            (data["auth_enabled"] as? Boolean)?.let { SecurePrefs.setBool(appContext, BankNotifyApp.PREF_SERVER, "server_auth_enabled", it) }
            (data["api_key"] as? String)?.let { SecurePrefs.setString(appContext, BankNotifyApp.PREF_SERVER, "server_api_key", it.trim()) }
            (data["server_port"] as? Number)?.let { newPort ->
                val p = newPort.toInt()
                if (p in 1024..65535 && p != port()) {
                    ApiServerService.restart(appContext, p)
                }
            }
            (data["update_url"] as? String)?.let { updateManager.checkUrl = it }
            call.respond(mapOf("success" to true, "message" to "Config updated"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode(400, "Bad Request"), mapOf("success" to false, "error" to (e.message ?: "Invalid request")))
        }
    }
}
