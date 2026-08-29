import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OrganisationsApi } from '../../api/api/organisations.service';
import { CreateOrganisationRequest } from '../../api/model/create-organisation-request';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';

type OrganisationType = CreateOrganisationRequest['organisationType'];

const ORGANISATION_TYPES: { value: OrganisationType; label: string }[] = [
  { value: 'HOSPITAL', label: 'Hospital' },
  { value: 'CLINIC', label: 'Clinic' },
  { value: 'UNIVERSITY', label: 'University' },
  { value: 'COMPANY', label: 'Company' },
  { value: 'OTHER', label: 'Other' },
];

@Component({
  selector: 'app-organisation-wizard',
  imports: [ReactiveFormsModule],
  styleUrl: '../auth/auth-shell.scss',
  template: `
    <div class="auth">
      <p class="auth__brand">Simplicity training</p>
      <div class="auth__card">
        <p class="badge badge--accent">Step 2 of 2</p>
        <h1>Set up your organisation</h1>
        <p class="auth__lede">
          You will be its first administrator, able to create teams and invite colleagues. If a
          colleague has already invited you, open the link in their email instead.
        </p>

        @if (error()) {
          <p class="notice notice--error" role="alert">{{ error() }}</p>
        }

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="name">Organisation name</label>
            <input id="name" formControlName="name" />
          </div>

          <div class="field">
            <label for="organisationType">Type</label>
            <select id="organisationType" formControlName="organisationType">
              @for (type of types; track type.value) {
                <option [value]="type.value">{{ type.label }}</option>
              }
            </select>
          </div>

          <div class="field">
            <label for="country">Country <span class="field__hint">(optional)</span></label>
            <input id="country" autocomplete="country-name" formControlName="country" />
          </div>

          <div class="auth__actions">
            <button class="button" type="submit" [disabled]="busy() || form.invalid">
              {{ busy() ? 'Creating…' : 'Create organisation' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
})
export class OrganisationWizard {
  private readonly api = inject(OrganisationsApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly types = ORGANISATION_TYPES;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    organisationType: ['HOSPITAL' as OrganisationType, Validators.required],
    country: [''],
  });

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    const { name, organisationType, country } = this.form.getRawValue();
    try {
      await firstValueFrom(
        this.api.createOrganisation({
          name: name.trim(),
          organisationType,
          country: country.trim() || undefined,
        }),
      );
      // The membership the server just created is what lets the guards through, so reload rather
      // than patching the cached user.
      await this.session.refresh();
      await this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('next') ?? '/dashboard');
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not create the organisation.'));
    } finally {
      this.busy.set(false);
    }
  }
}
