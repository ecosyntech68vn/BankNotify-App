package com.banknotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.banknotify.server.ApiServerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start_server", true)
            if (autoStart) {
                val port = prefs.getInt("server_port", 8765)
                ApiServerService.start(context, port)
            }
        }
    }
}
