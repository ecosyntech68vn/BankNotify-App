package com.banknotify.core.license

import android.content.Context
import android.util.Base64
import com.banknotify.core.SecurePrefs
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object LicenseManager {

    private const val PREF_NAME = "license"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_LICENSE_DATA = "license_data"
    private const val KEY_ACTIVATED_EMAIL = "activated_email"
    private const val KEY_EXPIRY = "expiry"

    private const val TRIAL_DAYS = 14
    private const val TRIAL_KEY = "trial_start"
    private const val TRIAL_PREF = "trial"

    private val PUBLIC_KEY_DER = Base64.decode(
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAz5XD3X4vYTRCD03UpMkeOJn6KWdDcqbFghCkV7b0zdiyIx0gM+M2D6M5grWWQgOn/M432v8t0hOi4KRX2TVIN9827FetMv2R+ZHo3b9BjLc/B++I6Cgm6vTNMAuOwx0bTbR8LJmoZxyxsyOikClkNjSVyvVZWJSXgzCfkL49joQQywbkHEGYXVG+wBvKalTXXWK7OOzcU+WTNjrgaNtwr/7kYLSlT21WsVnx9Z+gpaNnfY/OwLXem3rVo4Jz3wrScO5oXVVuNlCCz5AvDFJyhf3G1be2WQtZdgfOba9wJKfqhZJl5oELLpp8PpdIj0YIvQbR7Kvo03sk9+/0UQ/7ZQIDAQAB",
        Base64.DEFAULT
    )

    fun isLicensed(context: Context): Boolean {
        return SecurePrefs.getBool(context, PREF_NAME, KEY_ACTIVATED, false)
    }

    fun getLicensedEmail(context: Context): String {
        return SecurePrefs.getString(context, PREF_NAME, KEY_ACTIVATED_EMAIL, "")
    }

    fun getExpiryTimestamp(context: Context): Long {
        return SecurePrefs.getLong(context, PREF_NAME, KEY_EXPIRY, 0L)
    }

    fun activate(context: Context, licenseKey: String): LicenseResult {
        val trimmed = licenseKey.trim()
        val parts = trimmed.split(".")
        if (parts.size != 2) {
            return LicenseResult.INVALID_FORMAT
        }

        val dataB64 = parts[0]
        val sigB64 = parts[1]

        val dataBytes: ByteArray
        val sigBytes: ByteArray
        try {
            dataBytes = Base64.decode(dataB64, Base64.URL_SAFE)
            sigBytes = Base64.decode(sigB64, Base64.URL_SAFE)
        } catch (e: Exception) {
            return LicenseResult.INVALID_FORMAT
        }

        if (!verify(dataBytes, sigBytes)) {
            return LicenseResult.INVALID_SIGNATURE
        }

        val dataStr = String(dataBytes, Charsets.UTF_8)
        val sep = dataStr.indexOf('|')
        if (sep < 0) return LicenseResult.INVALID_FORMAT

        val email = dataStr.substring(0, sep)
        val expiryStr = dataStr.substring(sep + 1)
        val expiry = expiryStr.toLongOrNull()
        if (expiry == null || email.isBlank()) {
            return LicenseResult.INVALID_FORMAT
        }

        if (System.currentTimeMillis() > expiry) {
            return LicenseResult.EXPIRED
        }

        SecurePrefs.setString(context, PREF_NAME, KEY_LICENSE_DATA, trimmed)
        SecurePrefs.setString(context, PREF_NAME, KEY_ACTIVATED_EMAIL, email)
        SecurePrefs.setLong(context, PREF_NAME, KEY_EXPIRY, expiry)
        SecurePrefs.setBool(context, PREF_NAME, KEY_ACTIVATED, true)

        return LicenseResult.OK
    }

    fun getTrialDaysLeft(context: Context): Int {
        val trialPref = context.getSharedPreferences(TRIAL_PREF, Context.MODE_PRIVATE)
        val start = trialPref.getLong(TRIAL_KEY, 0L)
        if (start == 0L) return TRIAL_DAYS
        val elapsed = System.currentTimeMillis() - start
        val daysLeft = TRIAL_DAYS - (elapsed / (86400000L)).toInt()
        return daysLeft.coerceAtLeast(0)
    }

    fun startTrial(context: Context) {
        val trialPref = context.getSharedPreferences(TRIAL_PREF, Context.MODE_PRIVATE)
        if (trialPref.getLong(TRIAL_KEY, 0L) == 0L) {
            trialPref.edit().putLong(TRIAL_KEY, System.currentTimeMillis()).apply()
        }
    }

    fun isTrialExpired(context: Context): Boolean {
        return getTrialDaysLeft(context) <= 0
    }

    fun deactivate(context: Context) {
        SecurePrefs.setBool(context, PREF_NAME, KEY_ACTIVATED, false)
        SecurePrefs.setString(context, PREF_NAME, KEY_LICENSE_DATA, "")
        SecurePrefs.setString(context, PREF_NAME, KEY_ACTIVATED_EMAIL, "")
        SecurePrefs.setLong(context, PREF_NAME, KEY_EXPIRY, 0L)
    }

    private fun verify(data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val keySpec = X509EncodedKeySpec(PUBLIC_KEY_DER)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(keySpec)
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    sealed class LicenseResult {
        data object OK : LicenseResult()
        data object INVALID_FORMAT : LicenseResult()
        data object INVALID_SIGNATURE : LicenseResult()
        data object EXPIRED : LicenseResult()
    }
}
