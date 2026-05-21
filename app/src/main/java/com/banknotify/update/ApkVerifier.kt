package com.banknotify.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest
import java.security.cert.CertificateFactory

object ApkVerifier {

    private const val TAG = "ApkVerifier"

    fun verifyApk(context: Context, apkPath: String): Boolean {
        val installedHash = getInstalledCertHash(context) ?: run {
            Log.w(TAG, "Cannot read installed app certificate")
            return false
        }
        val apkHash = getApkCertHash(context, apkPath) ?: run {
            Log.w(TAG, "Cannot read APK certificate")
            return false
        }
        val match = MessageDigest.isEqual(installedHash, apkHash)
        if (!match) Log.e(TAG, "APK certificate mismatch! Rejecting install.")
        return match
    }

    private fun getInstalledCertHash(context: Context): ByteArray? {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val bytes = info.signatures?.firstOrNull()?.toByteArray() ?: return null
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(bytes.inputStream())
            MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        } catch (e: Exception) {
            Log.e(TAG, "getInstalledCertHash error", e)
            null
        }
    }

    private fun getApkCertHash(context: Context, apkPath: String): ByteArray? {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_SIGNATURES
            ) ?: return null
            val bytes = info.signatures?.firstOrNull()?.toByteArray() ?: return null
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(bytes.inputStream())
            MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        } catch (e: Exception) {
            Log.e(TAG, "getApkCertHash error", e)
            null
        }
    }
}
