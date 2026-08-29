import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CurrentUserApi } from '../../api/api/current-user.service';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';
import { PROFESSIONAL_ROLES } from './professional-roles';

@Component({
  selector: 'app-profile-wizard',
  imports: [ReactiveFormsModule],
  styleUrl: '../auth/auth-shell.scss',
  template: `
    <div class="auth">
      <p class="auth__brand">Simplicity training</p>
      <div class="auth__card">
        <p class="badge badge--accent">Step 1 of 2</p>
        <h1>Tell us about yourself</h1>
        <p class="auth__lede">
          Your name appears to colleagues in your teams. Your role tailors the training you see.
        </p>

        @if (error()) {
          <p class="notice notice--error" role="alert">{{ error() }}</p>
        }

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="fullName">Full name</label>
            <input id="fullName" autocomplete="name" formControlName="fullName" />
          </div>

          <div class="field">
            <label for="professionalRole">Professional role</label>
            <select id="professionalRole" formControlName="professionalRole">
              <option value="" disabled>Choose your role</option>
              @for (role of roles; track role) {
                <option [value]="role">{{ role }}</option>
              }
            </select>
          </div>

          <div class="field">
            <label for="phone">Phone number <span class="field__hint">(optional)</span></label>
            <input id="phone" type="tel" autocomplete="tel" formControlName="phone" />
          </div>

          <div class="auth__actions">
            <button class="button" type="submit" [disabled]="busy() || form.invalid">
              {{ busy() ? 'Saving…' : 'Continue' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
})
export class ProfileWizard {
  private readonly api = inject(CurrentUserApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly roles = PROFESSIONAL_ROLES;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    professionalRole: ['', Validators.required],
    phone: [''],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const { fullName, professionalRole, phone } = this.form.getRawValue();
    try {
      const user = await firstValueFrom(
        this.api.updateProfile({
          fullName: fullName.trim(),
          professionalRole,
          phone: phone.trim() || undefined,
        }),
      );
      this.session.set(user);

      const next = this.route.snapshot.queryParamMap.get('next');
      if ((user.organisations ?? []).length > 0) {
        await this.router.navigateByUrl(next ?? '/dashboard');
      } else {
        await this.router.navigate(['/welcome/organisation'], { queryParams: { next } });
      }
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not save your profile.'));
    } finally {
      this.busy.set(false);
    }
  }
}
