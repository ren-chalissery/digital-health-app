import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-confirm-email',
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './auth-shell.scss',
  template: `
    <div class="auth">
      <p class="auth__brand">Simplicity training</p>
      <div class="auth__card">
        <h1>Confirm your email</h1>
        <p class="auth__lede">
          We have sent a six-digit code to <strong>{{ email() }}</strong>.
        </p>

        @if (error()) {
          <p class="notice notice--error" role="alert">{{ error() }}</p>
        }
        @if (resent()) {
          <p class="notice notice--success" role="status">A new code is on its way.</p>
        }

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="code">Confirmation code</label>
            <input
              id="code"
              inputmode="numeric"
              autocomplete="one-time-code"
              formControlName="code"
            />
          </div>

          <div class="auth__actions">
            <button class="button" type="submit" [disabled]="busy() || form.invalid">
              {{ busy() ? 'Confirming…' : 'Confirm' }}
            </button>
          </div>
        </form>

        <p class="auth__footer">
          <button class="button--link" type="button" (click)="resend()">Send another code</button>
        </p>
      </div>

      <p class="auth__footer"><a routerLink="/sign-in">Back to sign in</a></p>
    </div>
  `,
})
export class ConfirmEmail {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly email = signal(
    inject(ActivatedRoute).snapshot.queryParamMap.get('email') ?? '',
  );
  protected readonly busy = signal(false);
  protected readonly resent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.minLength(6)]],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    try {
      await this.auth.confirmSignUp(this.email(), this.form.getRawValue().code.trim());
      // Confirming does not sign anybody in, so they land on sign-in with the address known.
      await this.router.navigate(['/sign-in'], { queryParams: { confirmed: 1 } });
    } catch (error) {
      this.error.set(problemMessage(error, 'That code was not accepted.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async resend(): Promise<void> {
    this.error.set(null);
    this.resent.set(false);
    try {
      await this.auth.resendConfirmationCode(this.email());
      this.resent.set(true);
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not send another code.'));
    }
  }
}
