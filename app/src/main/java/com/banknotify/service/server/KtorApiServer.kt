package com.banknotify.service.server

import android.content.Context
import android.util.Log
import com.banknotify.core.AppConfig
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.SecurePrefs
import com.banknotify.core.db.DatabaseHelper
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

    private data class RateEntry(var count: Int, var windowStart: Long)

    private const val RATE_MAX = 100
    private const val RATE_WINDOW_MS = 60000L
    private const val TAG = "KtorApiServer"

    private var server: Any? = null
    private val rateMap = mutableMapOf<String, RateEntry>()
    private val gson = GsonBuilder().create()

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var webhookManager: WebhookManager
    private lateinit var updateManager: UpdateManager
    private lateinit var appConfig: AppConfig
    private lateinit var appContext: Context

    @Volatile
    var port: Int = 8765
        private set

    val isRunning: Boolean get() = server is ApplicationEngine

    fun start(
        port: Int,
        dbHelper: DatabaseHelper,
        webhookManager: WebhookManager,
        updateManager: UpdateManager,
        appConfig: AppConfig,
        context: Context
    ) {
        if (server != null) return
        this.port = port
        this.dbHelper = dbHelper
        this.webhookManager = webhookManager
        this.updateManager = updateManager
        this.appConfig = appConfig
        this.appContext = context
        val eng = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                gson { setPrettyPrinting() }
            }
            intercept(ApplicationCallPipeline.Call) {
                val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim() ?: call.request.local.host
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
                apiRoutes(dbHelper, webhookManager, updateManager, appConfig, appContext, gson) { this@KtorApiServer.port }
            }
        }
        eng.start(wait = false)
        server = eng
        Log.i(TAG, "Server started on port $port")
    }

    fun stop() {
        (server as? ApplicationEngine)?.stop(1000, 3000)
        rateMap.clear()
        server = null
        Log.i(TAG, "Server stopped")
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
        if (!SecurePrefs.getBool(appContext, BankNotifyApp.PREF_SERVER, "server_auth_enabled")) return true
        val key = SecurePrefs.getString(appContext, BankNotifyApp.PREF_SERVER, "server_api_key")
        if (key.isBlank()) return true
        val provided = request.headers["X-API-Key"] ?: request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: ""
        return MessageDigest.isEqual(key.toByteArray(), provided.toByteArray())
    }
}
