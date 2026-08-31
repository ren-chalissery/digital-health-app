package io.simplicity.training.testing

import io.simplicity.training.foundation.SecureStore

/**
 * A [SecureStore] that keeps nothing. Tests need the contract, not the Keystore, and the real
 * implementation cannot run on the JVM at all.
 */
class InMemorySecureStore : SecureStore {

    private val values = mutableMapOf<String, String>()

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun get(key: String): String? = values[key]

    override fun remove(key: String) {
        values.remove(key)
    }
}
