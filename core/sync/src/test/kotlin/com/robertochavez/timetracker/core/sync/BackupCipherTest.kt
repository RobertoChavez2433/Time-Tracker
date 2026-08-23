package com.robertochavez.timetracker.core.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCipherTest {
    @Test fun `encrypted backup round trips`() {
        val cipher = BackupCipher()
        val plaintext = "project session data".toByteArray()
        assertArrayEquals(
            plaintext,
            cipher.decrypt(cipher.encrypt(plaintext, "strong passphrase".toCharArray()), "strong passphrase".toCharArray()),
        )
    }

    @Test(expected = Exception::class)
    fun `wrong password is rejected`() {
        val cipher = BackupCipher()
        cipher.decrypt(cipher.encrypt("data".toByteArray(), "strong passphrase".toCharArray()), "wrong password".toCharArray())
    }

    @Test(expected = Exception::class)
    fun `tampered backup is rejected before plaintext is returned`() {
        val encrypted = BackupCipher().encrypt("sensitive project data".toByteArray(), "strong passphrase".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()
        BackupCipher().decrypt(encrypted, "strong passphrase".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short passphrase is rejected`() {
        BackupCipher().encrypt("data".toByteArray(), "too short".toCharArray())
    }

    @Test fun `same plaintext produces different encrypted bytes`() {
        val cipher = BackupCipher()
        val first = cipher.encrypt("data".toByteArray(), "strong passphrase".toCharArray())
        val second = cipher.encrypt("data".toByteArray(), "strong passphrase".toCharArray())
        assertFalse(first.contentEquals(second))
    }
}
