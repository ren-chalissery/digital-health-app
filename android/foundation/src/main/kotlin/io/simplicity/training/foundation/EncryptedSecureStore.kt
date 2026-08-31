package io.simplicity.training.foundation

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The real [SecureStore], backed by the Android Keystore.
 *
 * Opening is lazy and deliberately unguarded. An exception here means the Keystore is unusable —
 * which happens on a device with a broken key provider — and the right answer is to fail loudly
 * rather than to write a refresh token where anything can read it.
 */
class EncryptedSecureStore(context: Context) : SecureStore {

    private val preferences by lazy {
        try {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            throw SecureStoreUnavailable(e)
        }
    }

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    private companion object {
        const val FILE_NAME = "simplicity.secure"
    }
}
