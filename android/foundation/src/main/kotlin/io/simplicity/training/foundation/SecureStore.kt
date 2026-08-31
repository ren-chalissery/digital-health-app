package io.simplicity.training.foundation

/**
 * Storage for values that must not be readable by anything else on the device: the refresh token,
 * and nothing else so far.
 *
 * There is deliberately no fallback. If encrypted storage cannot be opened, [put] throws rather
 * than quietly writing to ordinary preferences — a token in plain `SharedPreferences` is readable
 * by any process that gains the app's uid, and a silent downgrade is worse than a visible failure
 * because nobody finds out.
 */
interface SecureStore {

    /** @throws SecureStoreUnavailable if encrypted storage cannot be opened. */
    fun put(key: String, value: String)

    /** Null when absent. Also null when storage cannot be read, which is indistinguishable and fine. */
    fun get(key: String): String?

    fun remove(key: String)
}

class SecureStoreUnavailable(cause: Throwable) :
    IllegalStateException("Encrypted storage could not be opened", cause)
