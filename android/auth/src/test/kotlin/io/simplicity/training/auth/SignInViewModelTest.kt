package io.simplicity.training.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The same cases `SignInViewModelTests.swift` asserts.
 *
 * The one that matters most is the unconfirmed address: Cognito returning "not signed in" is a
 * routing outcome, not a failure, and reporting it as an error would tell somebody their password
 * was wrong when it was not.
 */
class SignInViewModelTest {

    private val auth = mockk<AuthService>()

    private fun sut(needsOnboarding: Boolean = false) =
        SignInViewModel(auth = auth, onSignedIn = { needsOnboarding })

    @Test
    fun `a successful sign-in reports signed in`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns true
        val model = sut()
        model.emailChanged("clinician@example.org")
        model.passwordChanged("Sup3rSecretPass")

        model.submitting()

        assertEquals(SignInOutcome.SIGNED_IN, model.state.value.outcome)
        assertNull(model.state.value.errorMessage)
    }

    @Test
    fun `an incomplete profile routes to onboarding rather than straight in`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns true
        val model = sut(needsOnboarding = true)
        model.emailChanged("clinician@example.org")
        model.passwordChanged("Sup3rSecretPass")

        model.submitting()

        assertEquals(SignInOutcome.NEEDS_ONBOARDING, model.state.value.outcome)
    }

    @Test
    fun `an unconfirmed address is an outcome, not an error`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns false
        val model = sut()
        model.emailChanged("clinician@example.org")
        model.passwordChanged("Sup3rSecretPass")

        model.submitting()

        assertEquals(SignInOutcome.NEEDS_CONFIRMATION, model.state.value.outcome)
        assertNull("telling them their password was wrong would be a lie", model.state.value.errorMessage)
    }

    @Test
    fun `a rejected sign-in reports a failure and no outcome`() = runTest {
        coEvery { auth.signIn(any(), any()) } throws IllegalStateException("nope")
        val model = sut()
        model.emailChanged("clinician@example.org")
        model.passwordChanged("wrong")

        model.submitting()

        assertEquals(R.string.sign_in_failed, model.state.value.errorMessage)
        assertNull(model.state.value.outcome)
    }

    @Test
    fun `an empty form asks for the fields without calling Cognito`() = runTest {
        val model = sut()

        model.submitting()

        assertEquals(R.string.sign_in_missing_fields, model.state.value.errorMessage)
        coVerify(exactly = 0) { auth.signIn(any(), any()) }
    }

    @Test
    fun `whitespace alone is not an email address`() = runTest {
        val model = sut()
        model.emailChanged("   ")
        model.passwordChanged("Sup3rSecretPass")

        model.submitting()

        assertEquals(R.string.sign_in_missing_fields, model.state.value.errorMessage)
        coVerify(exactly = 0) { auth.signIn(any(), any()) }
    }

    @Test
    fun `the busy flag is cleared once the attempt finishes`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns true
        val model = sut()
        model.emailChanged("clinician@example.org")
        model.passwordChanged("Sup3rSecretPass")

        model.submitting()

        assertFalse(model.state.value.isBusy)
    }
}
