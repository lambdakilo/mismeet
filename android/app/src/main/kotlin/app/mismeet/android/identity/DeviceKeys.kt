package app.mismeet.android.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.nostrdevkit.sdk.Keys

/**
 * The device key pair, spec section 3. The secret key is wrapped with an AES key that lives in
 * the Android Keystore and never leaves it; the wrapped bytes sit in private preferences.
 */
object DeviceKeys {
    private const val ALIAS = "mismeet-secret-key-wrap"
    private const val PREFS = "mismeet-keys"
    private const val ENTRY = "secret-key"
    private const val IV_LENGTH = 12

    fun loadOrCreate(context: Context): Keys {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(ENTRY, null)?.let { return Keys.parse(unwrap(it)) }
        val keys = Keys.generate()
        prefs.edit().putString(ENTRY, wrap(keys.secretKey().toHex())).apply()
        return keys
    }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun unwrap(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, sealed.copyOfRange(0, IV_LENGTH)))
        return String(cipher.doFinal(sealed.copyOfRange(IV_LENGTH, sealed.size)), Charsets.UTF_8)
    }
}
