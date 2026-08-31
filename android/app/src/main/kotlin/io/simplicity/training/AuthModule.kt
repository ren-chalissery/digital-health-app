package io.simplicity.training

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.simplicity.training.auth.AmplifyAuthService
import io.simplicity.training.auth.AuthService
import javax.inject.Singleton

/**
 * The app composes the modules; none of them depends on another's implementation. This is the only
 * place that knows Cognito is how authentication happens.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun authService(): AuthService = AmplifyAuthService()
}
