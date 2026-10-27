package com.example.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the agent's access token at rest with an AES-GCM key that lives in the Android Keystore (it never leaves
 * the phone's secure hardware). Until 2026-10-27 the token sat in plain text in the Room database, and the app's data
 * was in Android's cloud backup (docs/AUDIT_2026-10-27.md S5). EncryptedSharedPreferences is deprecated; this is the
 * Keystore + javax.crypto route Google points to instead.
 *
 * Stored form: "enc1:" + base64(12-byte IV + ciphertext). Anything else is a token from before this change.
 */
object TokenCipher {
    private const val PREFIX = "enc1:"
    private const val ALIAS = "deskai_token_key"
    private const val TAG = "TokenCipher"

    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)

    fun seal(plain: String): String {
        if (plain.isEmpty() || isSealed(plain)) return plain
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (e: Exception) {
            // No Keystore (a broken device or a unit test): keep the token usable rather than lose it
            Log.w(TAG, "couldn't encrypt the token: ${e.javaClass.simpleName}")
            plain
        }
    }

    /** The token, or "" when it can't be decrypted (the key is gone, e.g. after a factory reset): set it again. */
    fun open(stored: String): String {
        if (!isSealed(stored)) return stored
        return try {
            val bytes = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "couldn't decrypt the token: ${e.javaClass.simpleName}")
            ""
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }
}
