package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VaultPass", appName)
    }

    @Test
    fun `test pin hashing and verification`() {
        val pin = "123456"
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPin(pin, salt)

        assertTrue(CryptoManager.verifyPin(pin, salt, hash))
        assertTrue(!CryptoManager.verifyPin("654321", salt, hash))
    }

    @Test
    fun `test password generation and strength`() {
        val strongPass = CryptoManager.generatePassword(16)
        assertEquals(16, strongPass.length)
        val analysis = CryptoManager.analyzePassword(strongPass)
        assertTrue(analysis.score >= 50)
    }
}
