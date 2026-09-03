import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/problem';

/** Mirrors the Cognito password policy, so the rejection happens before the round trip. */
function strongPassword(control: AbstractControl) {
  const value = String(control.value ?? '');
  const failures: string[] = [];
  if (value.length < 12) failures.push('at least 12 characters');
  if (!/[a-z]/.test(value)) failures.push('a lower-case letter');
  if (!/[A-Z]/.test(value)) failures.push('an upper-case letter');
  if (!/[0-9]/.test(value)) failures.push('a number');
  return failures.length === 0 ? null : { weak: failures };
}

@Component({
  selector: 'app-sign-up',
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './auth-shell.scss',
  template: `
    <div class="auth">
      <p class="auth__brand">Simplicity training</p>
      <div class="auth__card">
        <h1>Create your account</h1>
        <p class="auth__lede">
          For mental health professionals learning to deliver Simplicity.
        </p>

        @if (error()) {
          <p class="notice notice--error" role="alert">{{ error() }}</p>
        }

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="email">Work email address</label>
            <input id="email" type="email" autocomplete="username" formControlName="email" />
          </div>

          <div class="field">
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              autocomplete="new-password"
              formControlName="password"
              [attr.aria-invalid]="passwordFailures().length > 0 && password.touched"
            />
            @if (passwordFailures().length > 0 && password.touched) {
              <p class="field__error">Needs {{ passwordFailures().join(', ') }}.</p>
            } @else {
              <p class="field__hint">At least 12 characters, with upper case, lower case, and a number.</p>
            }
          </div>

          <div class="auth__actions">
            <button class="button" type="submit" [disabled]="busy() || form.invalid">
              {{ busy() ? 'Creating…' : 'Create account' }}
            </button>
          </div>
        </form>
      </div>

      <p class="auth__footer">
        Already have an account? <a routerLink="/sign-in">Sign in</a>
      </p>

      <p class="auth__footer">
        <a routerLink="/privacy">Privacy policy</a>
      </p>
    </div>
  `,
})
export class SignUp {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, strongPassword]],
  });

  protected get password() {
    return this.form.controls.password;
  }

  protected passwordFailures(): string[] {
    return (this.password.errors?.['weak'] as string[]) ?? [];
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const { email, password } = this.form.getRawValue();
    try {
      await this.auth.signUp(email, password);
      await this.router.navigate(['/confirm-email'], { queryParams: { email } });
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not create the account.'));
    } finally {
      this.busy.set(false);
    }
  }
}
