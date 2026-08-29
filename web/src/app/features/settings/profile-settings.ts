import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { CurrentUserApi } from '../../api/api/current-user.service';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';
import { PROFESSIONAL_ROLES } from '../onboarding/professional-roles';

@Component({
  selector: 'app-profile-settings',
  imports: [ReactiveFormsModule],
  template: `
    <div class="card">
      <div class="card__title"><h2>Your profile</h2></div>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }
      @if (saved()) {
        <p class="notice notice--success" role="status">Your profile has been saved.</p>
      }

      <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
        <div class="field">
          <label for="email">Email address</label>
          <input id="email" [value]="email()" disabled />
          <p class="field__hint">Your email address is your sign-in and cannot be changed here.</p>
        </div>

        <div class="field">
          <label for="fullName">Full name</label>
          <input id="fullName" autocomplete="name" formControlName="fullName" />
        </div>

        <div class="field">
          <label for="professionalRole">Professional role</label>
          <select id="professionalRole" formControlName="professionalRole">
            @for (role of roles; track role) {
              <option [value]="role">{{ role }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="phone">Phone number <span class="field__hint">(optional)</span></label>
          <input id="phone" type="tel" autocomplete="tel" formControlName="phone" />
        </div>

        <div class="row">
          <button class="button" type="submit" [disabled]="busy() || form.invalid">
            {{ busy() ? 'Saving…' : 'Save changes' }}
          </button>
        </div>
      </form>
    </div>

    <div class="card">
      <div class="card__title"><h2>Your organisation</h2></div>
      @if (organisation(); as org) {
        <p>
          {{ org.name }}
          <span class="badge badge--accent">{{
            org.orgRole === 'ORG_ADMIN' ? 'Administrator' : 'Member'
          }}</span>
        </p>
        @if ((org.teams ?? []).length > 0) {
          <p class="field__hint">Teams</p>
          <div class="row">
            @for (team of org.teams; track team.teamId) {
              <span class="badge">{{ team.name }}</span>
            }
          </div>
        } @else {
          <p class="field__hint">You are not in any team yet.</p>
        }
      } @else {
        <p class="field__hint">You do not belong to an organisation.</p>
      }
    </div>
  `,
})
export class ProfileSettings {
  private readonly api = inject(CurrentUserApi);
  private readonly session = inject(SessionService);

  protected readonly roles = PROFESSIONAL_ROLES;
  protected readonly organisation = this.session.activeOrganisation;
  protected readonly busy = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: [this.session.user()?.fullName ?? '', [Validators.required, Validators.maxLength(200)]],
    professionalRole: [this.session.user()?.professionalRole ?? '', Validators.required],
    phone: [this.session.user()?.phone ?? ''],
  });

  protected email(): string {
    return this.session.user()?.email ?? '';
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.saved.set(false);

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
      this.saved.set(true);
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not save your profile.'));
    } finally {
      this.busy.set(false);
    }
  }
}
