package com.banknotify.core.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.banknotify.core.SecurePrefs

object BiometricLock {

    private const val PREF_NAME = "biometric"
    private const val KEY_ENABLED = "enabled"

    fun isSupported(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        return mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isEnabled(context: Context): Boolean =
        SecurePrefs.getBool(context, PREF_NAME, KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        SecurePrefs.setBool(context, PREF_NAME, KEY_ENABLED, enabled)

    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build())
    }
}
