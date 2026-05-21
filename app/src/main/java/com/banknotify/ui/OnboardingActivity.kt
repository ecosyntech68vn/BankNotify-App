package com.banknotify.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.banknotify.R
import com.banknotify.databinding.ActivityOnboardingBinding
import com.banknotify.service.listener.BankNotificationListener

class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding

    companion object {
        private const val PREF_ONBOARDING = "onboarding"
        private const val KEY_DONE = "done"

        fun isDone(context: Context): Boolean =
            context.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

        fun markDone(context: Context) {
            context.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnGrant.setOnClickListener {
            BankNotificationListener.openNotificationListenerSettings(this)
        }

        b.btnSkip.setOnClickListener {
            markDone(this)
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (BankNotificationListener.isNotificationListenerEnabled(this)) {
            markDone(this)
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
