import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './auth-shell.scss',
  template: `
    <div class="auth">
      <p class="auth__brand">Simplicity training</p>
      <div class="auth__card">
        @if (stage() === 'request') {
          <h1>Reset your password</h1>
          <p class="auth__lede">We will email you a code to set a new one.</p>

          @if (error()) {
            <p class="notice notice--error" role="alert">{{ error() }}</p>
          }

          <form class="stack" [formGroup]="requestForm" (ngSubmit)="request()">
            <div class="field">
              <label for="email">Email address</label>
              <input id="email" type="email" autocomplete="username" formControlName="email" />
            </div>
            <div class="auth__actions">
              <button class="button" type="submit" [disabled]="busy() || requestForm.invalid">
                {{ busy() ? 'Sending…' : 'Send code' }}
              </button>
            </div>
          </form>
        } @else {
          <h1>Choose a new password</h1>
          <p class="auth__lede">
            Enter the code sent to <strong>{{ requestForm.controls.email.value }}</strong>.
          </p>

          @if (error()) {
            <p class="notice notice--error" role="alert">{{ error() }}</p>
          }

          <form class="stack" [formGroup]="confirmForm" (ngSubmit)="confirm()">
            <div class="field">
              <label for="code">Confirmation code</label>
              <input id="code" inputmode="numeric" autocomplete="one-time-code" formControlName="code" />
            </div>
            <div class="field">
              <label for="newPassword">New password</label>
              <input
                id="newPassword"
                type="password"
                autocomplete="new-password"
                formControlName="newPassword"
              />
              <p class="field__hint">At least 12 characters, with upper case, lower case, and a number.</p>
            </div>
            <div class="auth__actions">
              <button class="button" type="submit" [disabled]="busy() || confirmForm.invalid">
                {{ busy() ? 'Saving…' : 'Set new password' }}
              </button>
            </div>
          </form>
        }
      </div>

      <p class="auth__footer"><a routerLink="/sign-in">Back to sign in</a></p>
    </div>
  `,
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly stage = signal<'request' | 'confirm'>('request');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly requestForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected readonly confirmForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.minLength(6)]],
    newPassword: ['', [Validators.required, Validators.minLength(12)]],
  });

  protected async request(): Promise<void> {
    if (this.requestForm.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.startPasswordReset(this.requestForm.getRawValue().email);
      this.stage.set('confirm');
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not start the reset.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async confirm(): Promise<void> {
    if (this.confirmForm.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const email = this.requestForm.getRawValue().email;
    const { code, newPassword } = this.confirmForm.getRawValue();
    try {
      await this.auth.confirmPasswordReset(email, code.trim(), newPassword);
      await this.router.navigate(['/sign-in'], { queryParams: { reset: 1 } });
    } catch (error) {
      this.error.set(problemMessage(error, 'That code was not accepted.'));
    } finally {
      this.busy.set(false);
    }
  }
}
