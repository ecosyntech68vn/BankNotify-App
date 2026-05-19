package com.banknotify.server

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.banknotify.BankNotifyApp

class ApiServerService : Service() {

    private var server: ApiServer? = null
    private val DEFAULT_PORT = 8765

    override fun onCreate() {
        super.onCreate()
        startServer(DEFAULT_PORT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        intent?.getIntExtra("port", 0)?.let { port ->
            if (port > 0 && port != server?.listeningPort) {
                stopServer()
                startServer(port)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer(port: Int) {
        try {
            server?.stop()
            server = ApiServer(port)
            server?.start()
            android.util.Log.i(TAG, "API Server started on port $port")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start server", e)
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
            server = null
            android.util.Log.i(TAG, "API Server stopped")
        } catch (_: Exception) {}
    }

    private fun createNotification(): Notification {
        val port = server?.listeningPort ?: DEFAULT_PORT
        return NotificationCompat.Builder(this, BankNotifyApp.CHANNEL_SERVER)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("BankNotify Server")
            .setContentText("API đang chạy tại http://localhost:$port")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "HTTP Server đang chạy\n" +
                "Port: $port\n" +
                "API: http://<device_ip>:$port/api"
            ))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "ApiServerService"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context, port: Int = 8765) {
            val intent = Intent(context, ApiServerService::class.java).apply {
                putExtra("port", port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ApiServerService::class.java))
        }

        fun restart(port: Int) {
            val ctx = BankNotifyApp.instance
            stop(ctx)
            start(ctx, port)
        }
    }
}
