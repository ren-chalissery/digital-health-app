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

  /**
   * Which organisation the clinician is working in. The server decides, so the choice survives a
   * refresh and follows them to another device; the fallback to the first membership only matters
   * for somebody who has never switched.
   */
  readonly activeOrganisation = computed<OrganisationMembershipResponse | null>(() => {
    const organisations = this.organisations();
    const chosen = this.currentUser()?.activeOrganisationId;
    return organisations.find((org) => org.orgId === chosen) ?? organisations[0] ?? null;
  });

  readonly canSwitchOrganisation = computed(() => this.organisations().length > 1);

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

  async setActiveOrganisation(orgId: string): Promise<void> {
    this.currentUser.set(
      await firstValueFrom(this.api.setActiveOrganisation({ organisationId: orgId })),
    );
  }

  async signOut(): Promise<void> {
    this.currentUser.set(null);
    await this.auth.signOut();
  }
}
