package com.robertochavez.timetracker.core.sync

import org.junit.Assert.assertArrayEquals
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
}
