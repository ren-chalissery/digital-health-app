package io.simplicity.training.auth

import androidx.annotation.StringRes
import io.simplicity.training.auth.R

/**
 * The Cognito pool's own rule, checked before the round trip.
 *
 * Duplicating a server-side rule on the client is usually a mistake, but a rejected password is the
 * one case where the round trip tells the person nothing they could not have been told
 * immediately — and Cognito's own message names the policy rather than what they typed.
 *
 * The rules match `PasswordPolicy.swift` exactly, including the order they are reported in, so the
 * two clients cannot disagree about whether a password is acceptable.
 */
object PasswordPolicy {

    private const val MINIMUM_LENGTH = 12

    /** Null when acceptable, otherwise the string resource explaining why not. */
    @StringRes
    fun validate(password: String): Int? = when {
        password.length < MINIMUM_LENGTH -> R.string.password_too_short
        password.none(Char::isUpperCase) -> R.string.password_needs_uppercase
        password.none(Char::isLowerCase) -> R.string.password_needs_lowercase
        password.none(Char::isDigit) -> R.string.password_needs_number
        else -> null
    }
}
