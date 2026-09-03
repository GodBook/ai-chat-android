package com.example.aichat.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the API key with an AES-GCM key held by Android Keystore. */
class ApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun save(value: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val plain = value.toByteArray(StandardCharsets.UTF_8)
        val encrypted = cipher.doFinal(plain)
        val stored = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
        preferences.edit().putString(KEY_VALUE, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun read(): String? {
        val encoded = preferences.getString(KEY_VALUE, null) ?: return null
        return runCatching {
            val stored = Base64.decode(encoded, Base64.DEFAULT)
            require(stored.isNotEmpty())
            val ivLength = stored[0].toInt() and 0xff
            require(ivLength > 0 && stored.size > ivLength + 1)
            val iv = stored.copyOfRange(1, ivLength + 1)
            val encrypted = stored.copyOfRange(ivLength + 1, stored.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_VALUE).apply()
    }

    /** Returns true only when the encrypted value can still be decrypted. */
    @Synchronized
    fun hasKey(): Boolean = preferences.contains(KEY_VALUE) && read() != null

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ai_chat_api_key"
        const val PREFERENCES_NAME = "secure_api_key"
        const val KEY_VALUE = "encrypted_value"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
