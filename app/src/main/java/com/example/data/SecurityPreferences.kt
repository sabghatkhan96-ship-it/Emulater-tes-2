package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SecurityPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "vaultpass_security_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_PIN_SALT = "key_pin_salt"
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_AUTO_LOCK_SECONDS = "key_auto_lock_seconds"
        private const val KEY_CLIPBOARD_CLEAR_SECONDS = "key_clipboard_clear_seconds"
        private const val KEY_LAST_UNLOCKED_TIME = "key_last_unlocked_time"
        private const val KEY_SAMPLE_SEEDED = "key_sample_seeded"
    }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var autoLockSeconds: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_SECONDS, 60)
        set(value) = prefs.edit().putInt(KEY_AUTO_LOCK_SECONDS, value).apply()

    var clipboardClearSeconds: Int
        get() = prefs.getInt(KEY_CLIPBOARD_CLEAR_SECONDS, 30)
        set(value) = prefs.edit().putInt(KEY_CLIPBOARD_CLEAR_SECONDS, value).apply()

    var lastUnlockedTime: Long
        get() = prefs.getLong(KEY_LAST_UNLOCKED_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UNLOCKED_TIME, value).apply()

    var isSampleSeeded: Boolean
        get() = prefs.getBoolean(KEY_SAMPLE_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SAMPLE_SEEDED, value).apply()

    val isPinConfigured: Boolean
        get() = !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()

    val pinSalt: String?
        get() = prefs.getString(KEY_PIN_SALT, null)

    val pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)

    fun saveMasterPin(salt: String, hash: String) {
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .apply()
    }

    fun removeMasterPin() {
        prefs.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .apply()
    }
}
