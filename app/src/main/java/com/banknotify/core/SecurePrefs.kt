package com.banknotify.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private val key = mutableMapOf<String, SharedPreferences>()

    private fun prefs(context: Context, name: String): SharedPreferences {
        return key.getOrPut(name) {
            val mk = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context, name + "_secure", mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun getString(context: Context, prefsName: String, key: String, default: String = ""): String {
        return prefs(context, prefsName).getString(key, default) ?: default
    }

    fun setString(context: Context, prefsName: String, key: String, value: String) {
        prefs(context, prefsName).edit().putString(key, value.trim()).apply()
    }

    fun getBool(context: Context, prefsName: String, key: String, default: Boolean = false): Boolean {
        return prefs(context, prefsName).getBoolean(key, default)
    }

    fun setBool(context: Context, prefsName: String, key: String, value: Boolean) {
        prefs(context, prefsName).edit().putBoolean(key, value).apply()
    }

    fun getInt(context: Context, prefsName: String, key: String, default: Int = 0): Int {
        return prefs(context, prefsName).getInt(key, default)
    }

    fun setInt(context: Context, prefsName: String, key: String, value: Int) {
        prefs(context, prefsName).edit().putInt(key, value).apply()
    }

    fun getLong(context: Context, prefsName: String, key: String, default: Long = 0L): Long {
        return prefs(context, prefsName).getLong(key, default)
    }

    fun setLong(context: Context, prefsName: String, key: String, value: Long) {
        prefs(context, prefsName).edit().putLong(key, value).apply()
    }
}
