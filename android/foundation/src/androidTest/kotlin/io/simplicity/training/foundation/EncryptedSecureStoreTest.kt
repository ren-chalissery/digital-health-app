package io.simplicity.training.foundation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real store held to the same contract the fake passes on the JVM.
 *
 * Without this the fake proves only that the interface is coherent. This is the test that would
 * catch `EncryptedSharedPreferences` behaving differently — returning "" for an absent key, say —
 * and so catch the fake being kinder than the thing it stands in for.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedSecureStoreTest {

    private lateinit var store: SecureStore

    @Before
    fun setUp() {
        store = EncryptedSecureStore(ApplicationProvider.getApplicationContext())
        store.remove("k")
        store.remove("refresh-token")
    }

    @Test
    fun aStoredValueComesBack() {
        store.put("refresh-token", "abc123")

        assertEquals("abc123", store.get("refresh-token"))
    }

    @Test
    fun anAbsentKeyIsNullRatherThanBlank() {
        assertNull(store.get("never-written"))
    }

    @Test
    fun writingTwiceKeepsTheSecondValue() {
        store.put("k", "first")
        store.put("k", "second")

        assertEquals("second", store.get("k"))
    }

    @Test
    fun aRemovedValueIsGone() {
        store.put("k", "v")

        store.remove("k")

        assertNull(store.get("k"))
    }
}
