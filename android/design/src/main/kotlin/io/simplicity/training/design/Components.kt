package io.simplicity.training.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** A labelled field. Password entry is masked, which is the only reason the flag exists. */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(Radius.Small),
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Disabled while busy, so a slow network cannot produce two sign-in attempts. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isBusy,
        shape = RoundedCornerShape(Radius.Medium),
        modifier = modifier.fillMaxWidth().heightIn(min = Layout.MinimumTapTarget),
    ) {
        if (isBusy) {
            // Sized down to the label's own height and tinted to match it, so the button does not
            // grow while it is working.
            CircularProgressIndicator(
                modifier = Modifier.size(Spacing.x5).padding(end = Spacing.x2),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(label)
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.x2)) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
