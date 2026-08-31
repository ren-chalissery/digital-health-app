package io.simplicity.training.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the shell's router acts on. The screen takes no navigation decision itself, which is what
 * keeps this module independent of the app's routes.
 */
enum class SignInOutcome { SIGNED_IN, NEEDS_CONFIRMATION, NEEDS_ONBOARDING }

data class SignInState(
    val email: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    @StringRes val errorMessage: Int? = null,
    val outcome: SignInOutcome? = null,
)

/**
 * Mirrors `SignInViewModel.swift`, including the part that is easy to read as an error and is not:
 * Cognito wanting the emailed code before a session exists is an outcome to route on, not a
 * failure to report.
 */
class SignInViewModel(
    private val auth: AuthService,
    private val onSignedIn: suspend () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun emailChanged(value: String) = _state.update { it.copy(email = value) }

    fun passwordChanged(value: String) = _state.update { it.copy(password = value) }

    fun submit() {
        viewModelScope.launch { submitting() }
    }

    suspend fun submitting() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(errorMessage = R.string.sign_in_missing_fields) }
            return
        }

        _state.update { it.copy(isBusy = true, errorMessage = null) }
        try {
            if (!auth.signIn(current.email, current.password)) {
                _state.update { it.copy(isBusy = false, outcome = SignInOutcome.NEEDS_CONFIRMATION) }
                return
            }
            val needsOnboarding = onSignedIn()
            _state.update {
                it.copy(
                    isBusy = false,
                    outcome = if (needsOnboarding) SignInOutcome.NEEDS_ONBOARDING else SignInOutcome.SIGNED_IN,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, errorMessage = R.string.sign_in_failed) }
        }
    }
}
