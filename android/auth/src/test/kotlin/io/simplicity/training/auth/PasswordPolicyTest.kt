package io.simplicity.training.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The same cases `PasswordPolicyTests.swift` asserts, so a password accepted by one client is
 * accepted by the other. Divergence here is invisible until a clinician switches phones.
 */
class PasswordPolicyTest {

    @Test
    fun `a password meeting every rule is accepted`() {
        assertNull(PasswordPolicy.validate("Sup3rSecretPass"))
    }

    @Test
    fun `eleven characters is too short, twelve is not`() {
        assertEquals(R.string.password_too_short, PasswordPolicy.validate("Sh0rtPass12"))
        assertNull(PasswordPolicy.validate("Sh0rtPass123"))
    }

    @Test
    fun `a capital is required`() {
        assertEquals(R.string.password_needs_uppercase, PasswordPolicy.validate("nocapitals123"))
    }

    @Test
    fun `a lowercase letter is required`() {
        assertEquals(R.string.password_needs_lowercase, PasswordPolicy.validate("NOLOWERCASE123"))
    }

    @Test
    fun `a digit is required`() {
        assertEquals(R.string.password_needs_number, PasswordPolicy.validate("NoDigitsAtAllHere"))
    }

    /** Length is reported before anything else, as on iOS, so the advice does not jump about. */
    @Test
    fun `a short password with several faults is told about length first`() {
        assertEquals(R.string.password_too_short, PasswordPolicy.validate("short"))
    }
}
