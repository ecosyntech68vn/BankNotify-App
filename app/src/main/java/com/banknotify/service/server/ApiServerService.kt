package com.banknotify.service.server

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.banknotify.core.BankNotifyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ApiServerService : Service() {

    override fun onCreate() {
        super.onCreate()
        startServer(BankNotifyApp.DEFAULT_PORT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        intent?.getIntExtra("port", 0)?.let { port ->
            if (port > 0 && port != KtorApiServer.port) {
                stopServer()
                startServer(port)
            }
        }
        isRunning = true
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        stopServer()
        isRunning = false
        super.onDestroy()
    }

    private fun startServer(port: Int) {
        try {
            KtorApiServer.stop()
            KtorApiServer.start(port)
            Log.d(TAG, "Server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
        }
    }

    private fun stopServer() {
        try { KtorApiServer.stop() } catch (e: Exception) { Log.w(TAG, "stopServer", e) }
    }

    private fun createNotification(): Notification {
        val currentPort = KtorApiServer.port
        return NotificationCompat.Builder(this, BankNotifyApp.CHANNEL_SERVER)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("BankNotify Server")
            .setContentText("API đang chạy tại http://localhost:$currentPort")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "HTTP Server đang chạy\nPort: $currentPort\nAPI: http://<ip>:$currentPort/api/v1/"))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "ApiServerService"
        private const val NOTIFICATION_ID = 1002
        @Volatile var isRunning = false

        fun start(context: Context, port: Int = BankNotifyApp.DEFAULT_PORT) {
            isRunning = true
            val intent = Intent(context, ApiServerService::class.java).apply { putExtra("port", port) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) { context.stopService(Intent(context, ApiServerService::class.java)) }

        fun restart(port: Int) {
            val ctx = BankNotifyApp.instance
            isRunning = false
            stop(ctx)
            start(ctx, port)
        }
    }
}
