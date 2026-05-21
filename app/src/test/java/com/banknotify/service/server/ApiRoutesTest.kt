package com.banknotify.service.server

import android.content.Context
import com.banknotify.core.AppConfig
import com.banknotify.core.db.DatabaseHelper
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionStatus
import com.banknotify.service.webhook.WebhookManager
import com.banknotify.update.UpdateManager
import com.google.gson.Gson
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ApiRoutesTest {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var webhookManager: WebhookManager
    private lateinit var updateManager: UpdateManager
    private lateinit var appConfig: AppConfig
    private lateinit var context: Context
    private val gson = Gson()

    @Before
    fun setUp() {
        dbHelper = mock()
        webhookManager = mock()
        updateManager = mock()
        appConfig = AppConfig(version = "1.0.0", build = 1)
        context = RuntimeEnvironment.getApplication()

        `when`(webhookManager.webhookUrl).thenReturn("https://example.com/hook")
        `when`(webhookManager.isEnabled).thenReturn(true)
        `when`(webhookManager.retryCount).thenReturn(3)

        `when`(updateManager.checkUrl).thenReturn("https://example.com/update")
    }

    private fun TestApplicationBuilder.configureApp() {
        application {
            install(ContentNegotiation) { gson { setPrettyPrinting() } }
            routing {
                apiRoutes(dbHelper, webhookManager, updateManager, appConfig, context, gson) { 8765 }
            }
        }
    }

    @Test
    fun `health endpoint returns ok`() = testApplication {
        configureApp()
        listOf("/api/v1/health", "/api/health").forEach { path ->
            client.get(path).apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, ""success"":true")
                assertContains(body, ""status"":""ok""")
                assertContains(body, ""version"":""1.0.0""")
            }
        }
    }

    @Test
    fun `transactions endpoint returns data`() = testApplication {
        val tx = Transaction(id = 1, bankCode = "TEST", content = "test", amount = 100.0, date = 1000L, status = TransactionStatus.PENDING)
        `when`(dbHelper.getTransactions(any())).thenReturn(listOf(tx))

        configureApp()
        client.get("/api/v1/transactions").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, ""success"":true")
            assertContains(body, ""total"":1")
        }
    }

    @Test
    fun `transactions recent endpoint returns data`() = testApplication {
        val tx = Transaction(id = 2, bankCode = "TEST", content = "recent", amount = 200.0, date = 2000L, status = TransactionStatus.PENDING)
        `when`(dbHelper.getRecentTransactions(20, 0)).thenReturn(listOf(tx))

        configureApp()
        client.get("/api/v1/transactions/recent").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), ""success"":true")
        }
    }

    @Test
    fun `transactions unread endpoint returns count`() = testApplication {
        `when`(dbHelper.getUnreadCount()).thenReturn(5)

        configureApp()
        client.get("/api/v1/transactions/unread").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), ""count"":5")
        }
    }

    @Test
    fun `transactions stats endpoint returns stats`() = testApplication {
        `when`(dbHelper.getTotalTransactions()).thenReturn(100)
        `when`(dbHelper.getTotalAmount()).thenReturn(9999.99)
        `when`(dbHelper.getUnreadCount()).thenReturn(10)

        configureApp()
        client.get("/api/v1/transactions/stats").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, ""total_transactions"":100")
            assertContains(body, ""total_amount"":9999.99")
            assertContains(body, ""unread_count"":10")
        }
    }

    @Test
    fun `transactions by id returns single transaction`() = testApplication {
        val tx = Transaction(id = 42, bankCode = "TEST", content = "single", amount = 500.0, date = 3000L, status = TransactionStatus.PENDING)
        `when`(dbHelper.getTransaction(42)).thenReturn(tx)

        configureApp()
        client.get("/api/v1/transactions/42").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), ""id"":42")
        }
    }

    @Test
    fun `transactions by id returns 404 for unknown`() = testApplication {
        `when`(dbHelper.getTransaction(999)).thenReturn(null)

        configureApp()
        client.get("/api/v1/transactions/999").apply {
            assertEquals(HttpStatusCode(404, "Not Found"), status)
        }
    }

    @Test
    fun `transactions by id returns 400 for invalid id`() = testApplication {
        configureApp()
        client.get("/api/v1/transactions/abc").apply {
            assertEquals(HttpStatusCode(400, "Bad Request"), status)
        }
    }

    @Test
    fun `confirm transaction returns 200`() = testApplication {
        val tx = Transaction(id = 1, bankCode = "TEST", content = "x", amount = 1.0, date = 1L, status = TransactionStatus.PENDING)
        `when`(dbHelper.getTransaction(1)).thenReturn(tx)

        configureApp()
        client.post("/api/v1/transactions/1/confirm").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), ""message"":""Confirmed""")
        }
        verify(dbHelper).updateStatus(1, TransactionStatus.CONFIRMED)
    }

    @Test
    fun `confirm missing transaction returns 404`() = testApplication {
        `when`(dbHelper.getTransaction(1)).thenReturn(null)

        configureApp()
        client.post("/api/v1/transactions/1/confirm").apply {
            assertEquals(HttpStatusCode(404, "Not Found"), status)
        }
    }

    @Test
    fun `delete transaction returns 200`() = testApplication {
        configureApp()
        client.delete("/api/v1/transactions/42").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), ""Deleted""")
        }
        verify(dbHelper).deleteTransaction(42)
    }

    @Test
    fun `webhook get returns config`() = testApplication {
        configureApp()
        client.get("/api/v1/webhook").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, ""url"":""https://example.com/hook""")
            assertContains(body, ""enabled"":true")
            assertContains(body, ""retry_count"":3")
        }
    }

    @Test
    fun `webhook post updates config`() = testApplication {
        configureApp()
        client.post("/api/v1/webhook") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://new.hook","enabled":false,"retry_count":5,"secret":"s3cret"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        verify(webhookManager).webhookUrl = "https://new.hook"
        verify(webhookManager).isEnabled = false
        verify(webhookManager).retryCount = 5
        verify(webhookManager).secret = "s3cret"
    }

    @Test
    fun `webhook post rejects invalid url`() = testApplication {
        configureApp()
        client.post("/api/v1/webhook") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"not-a-url"}""")
        }.apply {
            assertEquals(HttpStatusCode(400, "Bad Request"), status)
        }
    }

    @Test
    fun `webhook test dispatches`() = testApplication {
        configureApp()
        client.post("/api/v1/webhook/test") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://example.com/test"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        verify(webhookManager).testWebhook(eq("https://example.com/test"), any())
    }

    @Test
    fun `webhook test rejects missing url`() = testApplication {
        configureApp()
        client.post("/api/v1/webhook/test") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }.apply {
            assertEquals(HttpStatusCode(400, "Bad Request"), status)
        }
    }

    @Test
    fun `update check returns info`() = testApplication {
        configureApp()
        listOf("GET", "POST").forEach { method ->
            val response = when (method) {
                "GET" -> client.get("/api/v1/update/check")
                else -> client.post("/api/v1/update/check") { contentType(ContentType.Application.Json); setBody("{}") }
            }
            response.apply {
                assertEquals(HttpStatusCode.OK, status)
                val body = bodyAsText()
                assertContains(body, ""has_update"":false")
                assertContains(body, ""current_version"":""1.0.0""")
                assertContains(body, ""check_url"":""https://example.com/update""")
            }
        }
    }

    @Test
    fun `update info returns info`() = testApplication {
        configureApp()
        client.get("/api/v1/update/info").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, ""check_url"":""https://example.com/update""")
            assertContains(body, ""configured"":true")
        }
    }

    @Test
    fun `update url get returns url`() = testApplication {
        configureApp()
        client.get("/api/v1/update/url").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), ""url"":""https://example.com/update""")
        }
    }

    @Test
    fun `update url post updates url`() = testApplication {
        configureApp()
        client.post("/api/v1/update/url") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://new.update/check"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        verify(updateManager).checkUrl = "https://new.update/check"
    }

    @Test
    fun `config get returns all settings`() = testApplication {
        configureApp()
        client.get("/api/v1/config").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertContains(body, ""server_port"":8765")
            assertContains(body, ""webhook_url"":""https://example.com/hook""")
            assertContains(body, ""update_url"":""https://example.com/update""")
        }
    }

    @Test
    fun `config post updates auth and settings`() = testApplication {
        configureApp()
        client.post("/api/v1/config") {
            contentType(ContentType.Application.Json)
            setBody("""{"auth_enabled":true,"api_key":"new-key","update_url":"https://new.update"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        verify(updateManager).checkUrl = "https://new.update"
    }

    @Test
    fun `config post rejects invalid json`() = testApplication {
        configureApp()
        client.post("/api/v1/config") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }.apply {
            assertEquals(HttpStatusCode(400, "Bad Request"), status)
        }
    }

    @Test
    fun `both prefixes work identically`() = testApplication {
        configureApp()
        val v1 = client.get("/api/v1/health").bodyAsText()
        val v2 = client.get("/api/health").bodyAsText()
        assertEquals(v1, v2)
    }
}
