package com.robertochavez.timetracker.core.sync

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class BackupCipher @Inject constructor() {
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= MINIMUM_PASSPHRASE_LENGTH) { "Use a passphrase with at least 10 characters." }
        val salt = random(SALT_BYTES)
        val iv = random(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(MAGIC.size + salt.size + iv.size + encrypted.size)
            .put(MAGIC).put(salt).put(iv).put(encrypted).array()
    }

    fun decrypt(encrypted: ByteArray, passphrase: CharArray): ByteArray {
        require(encrypted.size > MAGIC.size + SALT_BYTES + IV_BYTES) { "Backup is incomplete." }
        val buffer = ByteBuffer.wrap(encrypted)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "This is not a Time Tracker encrypted backup." }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            doFinal(ciphertext)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)).encoded,
        "AES",
    )

    private fun random(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)

    private companion object {
        val MAGIC = "TTBACKUP1".toByteArray()
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val ITERATIONS = 210_000
        const val MINIMUM_PASSPHRASE_LENGTH = 10
    }
}
