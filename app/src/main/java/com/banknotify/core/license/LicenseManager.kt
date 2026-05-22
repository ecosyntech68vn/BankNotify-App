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

    private const val TRIAL_DAYS = 7
    private const val TRIAL_KEY = "trial_start"
    private const val TRIAL_PREF = "trial"

    private val PUBLIC_KEY_DER = byteArrayOf(
        0x30, 0x82, 0x01, 0x22, 0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01,
        0x01, 0x05, 0x00, 0x03, 0x82, 0x01, 0x0f, 0x00, 0x30, 0x82, 0x01, 0x0a, 0x02, 0x82, 0x01, 0x01,
        0x00, 0xd8, 0x90, 0xf0, 0x52, 0xf6, 0x54, 0x1f, 0x95, 0x12, 0x17, 0xe4, 0xf3, 0xbe, 0xbb, 0x08,
        0xdc, 0xbb, 0xd1, 0x55, 0xcd, 0xdf, 0xf9, 0xd1, 0xa1, 0xcf, 0x5b, 0xf9, 0xd0, 0x12, 0x62, 0x8d,
        0x71, 0x49, 0x88, 0x57, 0x22, 0xe3, 0xd5, 0xed, 0x28, 0x94, 0x35, 0x18, 0xf1, 0xb7, 0x67, 0x02,
        0x95, 0x35, 0x0e, 0x9d, 0xc6, 0x9c, 0x36, 0xa2, 0x87, 0x8f, 0x71, 0x08, 0xab, 0x86, 0xaf, 0x0c,
        0xe8, 0x9b, 0xe3, 0x9a, 0x7f, 0xaa, 0xc5, 0xb3, 0x4d, 0x21, 0xd9, 0x8b, 0x84, 0x11, 0x11, 0x84,
        0xe5, 0x90, 0xed, 0x35, 0x26, 0x2f, 0x5b, 0x01, 0x6a, 0x91, 0x6f, 0x58, 0x7a, 0x2a, 0x8e, 0x46,
        0x0e, 0x19, 0xd6, 0x30, 0xda, 0x01, 0xf8, 0xe2, 0xce, 0x4b, 0xb0, 0xdc, 0x0d, 0xee, 0x41, 0x69,
        0x35, 0x1c, 0xc0, 0x31, 0xe4, 0xb3, 0xfe, 0x62, 0x48, 0xb2, 0x4c, 0x8f, 0x8c, 0x3b, 0xb7, 0x1f,
        0xc3, 0x90, 0x9d, 0x15, 0x9a, 0x47, 0x22, 0x9b, 0x80, 0xe0, 0xc9, 0xa9, 0x75, 0x3c, 0x30, 0xa1,
        0x70, 0xe7, 0xb8, 0x68, 0xa5, 0x5b, 0x14, 0x66, 0xf7, 0xaf, 0x46, 0xb3, 0xf8, 0x60, 0xc9, 0x43,
        0xf7, 0xf3, 0x81, 0x22, 0xf9, 0x83, 0x8d, 0x1d, 0x55, 0xf0, 0xa1, 0x01, 0xaa, 0x7e, 0x8c, 0xc9,
        0x5b, 0x91, 0x7d, 0x74, 0x28, 0x44, 0x0d, 0xe1, 0x48, 0xa2, 0xd9, 0x6c, 0x8a, 0x90, 0x40, 0x8f,
        0x8f, 0xc5, 0x3a, 0xa4, 0x00, 0x95, 0x0e, 0x4b, 0x2d, 0x11, 0x09, 0xca, 0xfc, 0xc1, 0x48, 0x61,
        0xdf, 0x82, 0x13, 0x4a, 0xff, 0x5e, 0xe3, 0x15, 0xe9, 0x86, 0xc7, 0xfc, 0x36, 0x17, 0x42, 0x5c,
        0x45, 0xa9, 0x2d, 0x63, 0x2a, 0x04, 0x89, 0xe8, 0xa1, 0x03, 0xe8, 0x53, 0x3c, 0xe8, 0x50, 0x86,
        0x62, 0x27, 0x5a, 0x7c, 0xba, 0xc1, 0xbc, 0x1f, 0x52, 0x1a, 0x9c, 0xbd, 0x03, 0x9f, 0xe2, 0x26,
        0x35, 0x02, 0x03, 0x01, 0x00, 0x01
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
