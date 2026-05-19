package com.banknotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.banknotify.core.BankNotifyApp
import com.banknotify.service.server.ApiServerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(BankNotifyApp.PREF_SERVER, Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_start_server", true)) {
                val port = prefs.getInt("server_port", 8765)
                ApiServerService.start(context, port)
            }
        }
    }
}
