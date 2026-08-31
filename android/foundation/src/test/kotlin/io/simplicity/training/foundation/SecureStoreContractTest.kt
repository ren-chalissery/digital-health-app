package io.simplicity.training.foundation

import io.simplicity.training.testing.InMemorySecureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The contract every [SecureStore] has to honour.
 *
 * Run here against the in-memory fake, because the real implementation is
 * `EncryptedSharedPreferences` and cannot start on the JVM — it needs the Android Keystore. The
 * same cases run against the real one in `androidTest`, so the fake cannot drift into being a
 * kinder store than the thing it stands in for.
 */
class SecureStoreContractTest {

    private val store: SecureStore = InMemorySecureStore()

    @Test
    fun `a stored value comes back`() {
        store.put("refresh-token", "abc123")

        assertEquals("abc123", store.get("refresh-token"))
    }

    @Test
    fun `an absent key is null rather than blank`() {
        assertNull(store.get("never-written"))
    }

    @Test
    fun `writing twice keeps the second value`() {
        store.put("k", "first")
        store.put("k", "second")

        assertEquals("second", store.get("k"))
    }

    @Test
    fun `a removed value is gone`() {
        store.put("k", "v")

        store.remove("k")

        assertNull(store.get("k"))
    }

    @Test
    fun `removing something absent is not an error`() {
        store.remove("never-written")
    }
}
