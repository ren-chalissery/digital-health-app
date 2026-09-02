package io.simplicity.training

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import io.simplicity.training.auth.AmplifyAuthService
import io.simplicity.training.auth.SignInScreen
import io.simplicity.training.auth.SignInViewModel
import io.simplicity.training.design.SimplicityTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: io.simplicity.training.auth.AuthService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimplicityTheme {
                // onSignedIn returns whether onboarding is needed. Until :services exists there is
                // nothing to ask, so it is false — the router grows with the next slice.
                val model = viewModel {
                    SignInViewModel(auth = auth, onSignedIn = { false })
                }
                SignInScreen(model = model)
            }
        }
    }
}
