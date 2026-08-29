import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CurrentUserApi } from '../api/api/current-user.service';
import { CurrentUserResponse } from '../api/model/current-user-response';
import { OrganisationMembershipResponse } from '../api/model/organisation-membership-response';
import { AuthService } from './auth/auth.service';

/**
 * Holds the answer to GET /api/v1/me for the current session.
 *
 * <p>Onboarding state comes from the server rather than being inferred here, so the web, iOS, and
 * Android clients cannot disagree about when a clinician has finished setting up.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(CurrentUserApi);
  private readonly auth = inject(AuthService);

  private readonly currentUser = signal<CurrentUserResponse | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly organisations = computed(() => this.currentUser()?.organisations ?? []);
  readonly profileCompleted = computed(() => this.currentUser()?.profileCompleted ?? false);
  readonly hasOrganisation = computed(() => this.organisations().length > 0);

  /** Phase 1 puts a clinician in one organisation; the model allows more, so this picks the first. */
  readonly activeOrganisation = computed<OrganisationMembershipResponse | null>(
    () => this.organisations()[0] ?? null,
  );

  readonly isOrgAdmin = computed(() => this.activeOrganisation()?.orgRole === 'ORG_ADMIN');

  async refresh(): Promise<CurrentUserResponse> {
    const user = await firstValueFrom(this.api.getCurrentUser());
    this.currentUser.set(user);
    return user;
  }

  /** Returns the cached answer if there is one, so guards on a single navigation ask once. */
  async ensureLoaded(): Promise<CurrentUserResponse> {
    return this.currentUser() ?? (await this.refresh());
  }

  set(user: CurrentUserResponse): void {
    this.currentUser.set(user);
  }

  async signOut(): Promise<void> {
    this.currentUser.set(null);
    await this.auth.signOut();
  }
}
