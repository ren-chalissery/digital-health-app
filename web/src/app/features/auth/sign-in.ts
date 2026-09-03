import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'app-sign-in',
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './auth-shell.scss',
  template: `
    <div class="auth">
      <p class="auth__brand">Simplicity training</p>
      <div class="auth__card">
        <h1>Sign in</h1>
        <p class="auth__lede">Continue your training and review your reflections.</p>

        @if (error()) {
          <p class="notice notice--error" role="alert">{{ error() }}</p>
        }

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="email">Email address</label>
            <input id="email" type="email" autocomplete="username" formControlName="email" />
          </div>

          <div class="field">
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              autocomplete="current-password"
              formControlName="password"
            />
          </div>

          <div class="auth__actions">
            <button class="button" type="submit" [disabled]="busy() || form.invalid">
              {{ busy() ? 'Signing in…' : 'Sign in' }}
            </button>
          </div>
        </form>

        <p class="auth__footer">
          <a routerLink="/forgot-password">Forgotten your password?</a>
        </p>
      </div>

      <p class="auth__footer">
        No account yet? <a routerLink="/sign-up">Create one</a>
      </p>

      <p class="auth__footer">
        <a routerLink="/privacy">Privacy policy</a>
      </p>
    </div>
  `,
})
export class SignIn {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const { email, password } = this.form.getRawValue();
    try {
      const signedIn = await this.auth.signIn(email, password);
      if (!signedIn) {
        // Cognito wants the emailed code before it will issue a session.
        await this.router.navigate(['/confirm-email'], { queryParams: { email } });
        return;
      }
      await this.afterSignIn();
    } catch (error) {
      if ((error as { name?: string }).name === 'UserNotConfirmedException') {
        await this.router.navigate(['/confirm-email'], { queryParams: { email } });
        return;
      }
      this.error.set(problemMessage(error, 'Could not sign you in. Check your details.'));
    } finally {
      this.busy.set(false);
    }
  }

  /** The server decides what is still outstanding; the client only routes on the answer. */
  private async afterSignIn(): Promise<void> {
    const user = await this.session.refresh();
    const next = this.route.snapshot.queryParamMap.get('next');

    if (!user.profileCompleted) {
      await this.router.navigate(['/welcome/profile'], { queryParams: { next } });
    } else if ((user.organisations ?? []).length === 0) {
      await this.router.navigate(['/welcome/organisation'], { queryParams: { next } });
    } else {
      await this.router.navigateByUrl(next ?? '/dashboard');
    }
  }
}
