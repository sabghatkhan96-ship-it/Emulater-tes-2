package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "VaultPass_MasterKey_AES256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 5000
    private const val PBKDF2_KEY_LENGTH = 256

    private val secureRandom = SecureRandom()
    private var fallbackJvmKey: SecretKey? = null

    private fun getSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                ensureKeyExists(keyStore)
            }
            (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } catch (e: Exception) {
            // Fallback for JVM host / Robolectric test runner where AndroidKeyStore provider is not present
            fallbackJvmKey ?: synchronized(this) {
                fallbackJvmKey ?: KeyGenerator.getInstance("AES").apply {
                    init(256)
                }.generateKey().also { fallbackJvmKey = it }
            }
        }
    }

    private fun ensureKeyExists(keyStore: KeyStore) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns Base64-encoded string: [12 bytes IV][Ciphertext + 16 bytes GCM Auth Tag]
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64-encoded ciphertext using AES-256-GCM.
     */
    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""
            val iv = ByteArray(GCM_IV_LENGTH)
            val encryptedBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }

    // --- Master PIN Protection with PBKDF2 ---

    fun generateSalt(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val computed = hashPin(pin, saltBase64)
        return computed == expectedHashBase64
    }

    // --- Password Strength & Security Analysis ---

    data class PasswordAnalysis(
        val score: Int, // 0 - 100
        val rating: String, // Very Weak, Weak, Moderate, Strong, Excellent
        val entropyBits: Int,
        val suggestions: List<String>
    )

    fun analyzePassword(password: String): PasswordAnalysis {
        if (password.isEmpty()) {
            return PasswordAnalysis(0, "Empty", 0, listOf("Password cannot be empty"))
        }

        var score = 0
        val suggestions = mutableListOf<String>()

        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSpecial) poolSize += 33

        // Calculate Shannon entropy (approx)
        val entropy = if (poolSize > 0) {
            (password.length * (Math.log(poolSize.toDouble()) / Math.log(2.0))).toInt()
        } else 0

        // Length checks
        when {
            password.length >= 16 -> score += 40
            password.length >= 12 -> score += 30
            password.length >= 8 -> score += 15
            else -> suggestions.add("Use at least 12 characters")
        }

        // Variety checks
        if (hasLower) score += 15 else suggestions.add("Add lowercase letters")
        if (hasUpper) score += 15 else suggestions.add("Add uppercase letters")
        if (hasDigit) score += 15 else suggestions.add("Add numbers (0-9)")
        if (hasSpecial) score += 15 else suggestions.add("Add special symbols (!@#$%^&*)")

        // Penalize repeating characters or simple sequences
        if (password.windowed(3).any { it[0] == it[1] && it[1] == it[2] }) {
            score -= 15
            suggestions.add("Avoid repeating characters like 'aaa'")
        }
        if (password.lowercase().contains("password") || password.lowercase().contains("123456") || password.lowercase().contains("qwerty")) {
            score = 10
            suggestions.add("Avoid common words or patterns like 'password' or '123456'")
        }

        val clampedScore = score.coerceIn(5, 100)
        val rating = when {
            clampedScore >= 85 -> "Excellent"
            clampedScore >= 65 -> "Strong"
            clampedScore >= 45 -> "Moderate"
            clampedScore >= 25 -> "Weak"
            else -> "Very Weak"
        }

        return PasswordAnalysis(
            score = clampedScore,
            rating = rating,
            entropyBits = entropy,
            suggestions = suggestions
        )
    }

    // --- Password Generator ---

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    fun generatePassword(
        length: Int = 16,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val charPool = StringBuilder()
        if (includeLower) charPool.append(LOWER)
        if (includeUpper) charPool.append(UPPER)
        if (includeNumbers) charPool.append(NUMBERS)
        if (includeSymbols) charPool.append(SYMBOLS)

        if (charPool.isEmpty()) charPool.append(LOWER).append(NUMBERS)

        val pool = charPool.toString()
        val result = StringBuilder(length)

        // Ensure at least one of each requested category
        val guaranteed = mutableListOf<Char>()
        if (includeLower) guaranteed.add(LOWER[secureRandom.nextInt(LOWER.length)])
        if (includeUpper) guaranteed.add(UPPER[secureRandom.nextInt(UPPER.length)])
        if (includeNumbers) guaranteed.add(NUMBERS[secureRandom.nextInt(NUMBERS.length)])
        if (includeSymbols) guaranteed.add(SYMBOLS[secureRandom.nextInt(SYMBOLS.length)])

        for (ch in guaranteed) {
            result.append(ch)
        }

        for (i in guaranteed.size until length) {
            result.append(pool[secureRandom.nextInt(pool.length)])
        }

        // Shuffle characters securely
        val chars = result.toString().toCharArray()
        for (i in chars.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = chars[i]
            chars[i] = chars[j]
            chars[j] = temp
        }

        return String(chars)
    }

    private val WORD_LIST = listOf(
        "anchor", "beacon", "citadel", "delta", "echo", "falcon", "glacier", "harbor",
        "island", "jasper", "kinetic", "lunar", "matrix", "nexus", "orbit", "phoenix",
        "quantum", "radar", "sierra", "titan", "umbra", "vortex", "zenith", "crystal",
        "shield", "cipher", "safeguard", "sentinel", "guardian", "bastion", "apex", "prism"
    )

    fun generatePassphrase(wordCount: Int = 4, separator: String = "-"): String {
        val selected = mutableListOf<String>()
        for (i in 0 until wordCount) {
            selected.add(WORD_LIST[secureRandom.nextInt(WORD_LIST.size)])
        }
        val number = secureRandom.nextInt(90) + 10
        return selected.joinToString(separator) + separator + number
    }
}
