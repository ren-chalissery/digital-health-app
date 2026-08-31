package io.simplicity.training.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.simplicity.training.design.ErrorBanner
import io.simplicity.training.design.FormField
import io.simplicity.training.design.PrimaryButton

@Composable
fun SignInScreen(model: SignInViewModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Sign in", style = MaterialTheme.typography.headlineMedium)

        FormField(
            label = "Email address",
            value = state.email,
            onValueChange = model::emailChanged,
        )
        FormField(
            label = "Password",
            value = state.password,
            onValueChange = model::passwordChanged,
            isPassword = true,
        )

        state.errorMessage?.let { ErrorBanner(stringResource(it)) }

        PrimaryButton(label = "Sign in", onClick = model::submit, isBusy = state.isBusy)
    }
}
