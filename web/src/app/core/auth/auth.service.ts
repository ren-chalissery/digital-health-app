import { Injectable } from '@angular/core';
import {
  confirmResetPassword,
  confirmSignUp,
  fetchAuthSession,
  getCurrentUser,
  resendSignUpCode,
  resetPassword,
  signIn,
  signOut,
  signUp,
} from 'aws-amplify/auth';

/**
 * The only place that talks to Cognito.
 *
 * <p>Everything else in the application deals in "signed in or not" and an access token, so
 * swapping the identity provider would not reach past this file.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  async signUp(email: string, password: string): Promise<void> {
    await signUp({
      username: email,
      password,
      options: { userAttributes: { email } },
    });
  }

  async confirmSignUp(email: string, code: string): Promise<void> {
    await confirmSignUp({ username: email, confirmationCode: code });
  }

  async resendConfirmationCode(email: string): Promise<void> {
    await resendSignUpCode({ username: email });
  }

  /**
   * Returns false when Cognito needs something more before the session exists — an unconfirmed
   * address, or a challenge. The caller routes on that rather than treating it as an error.
   */
  async signIn(email: string, password: string): Promise<boolean> {
    const result = await signIn({ username: email, password });
    return result.isSignedIn;
  }

  async signOut(): Promise<void> {
    await signOut();
  }

  async startPasswordReset(email: string): Promise<void> {
    await resetPassword({ username: email });
  }

  async confirmPasswordReset(email: string, code: string, newPassword: string): Promise<void> {
    await confirmResetPassword({ username: email, confirmationCode: code, newPassword });
  }

  async isSignedIn(): Promise<boolean> {
    try {
      await getCurrentUser();
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Amplify refreshes the access token here when it is close to expiry, which is why every
   * request asks for it rather than caching one.
   */
  async accessToken(): Promise<string | null> {
    try {
      const session = await fetchAuthSession();
      return session.tokens?.accessToken?.toString() ?? null;
    } catch {
      return null;
    }
  }
}
