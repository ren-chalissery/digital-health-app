import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  provideRouter,
  UrlTree,
} from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { CurrentUserResponse } from '../../api/model/current-user-response';
import { SessionService } from '../session.service';
import { AuthService } from './auth.service';
import { onboardedGuard, onboardingGuard, orgAdminGuard } from './guards';

class FakeAuthService {
  signedIn = true;
  async isSignedIn(): Promise<boolean> {
    return this.signedIn;
  }
}

class FakeSessionService {
  user: CurrentUserResponse = { profileCompleted: true, organisations: [] };

  async ensureLoaded(): Promise<CurrentUserResponse> {
    return this.user;
  }
  profileCompleted(): boolean {
    return this.user.profileCompleted ?? false;
  }
  hasOrganisation(): boolean {
    return (this.user.organisations ?? []).length > 0;
  }
  isOrgAdmin(): boolean {
    return (this.user.organisations ?? [])[0]?.orgRole === 'ORG_ADMIN';
  }
}

describe('route guards', () => {
  let auth: FakeAuthService;
  let session: FakeSessionService;

  const run = (guard: typeof onboardedGuard, url = '/dashboard') =>
    TestBed.runInInjectionContext(() =>
      guard(
        {} as ActivatedRouteSnapshot,
        { url } as RouterStateSnapshot,
      ),
    ) as Promise<boolean | UrlTree>;

  const destination = (result: boolean | UrlTree) =>
    result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;

  beforeEach(() => {
    auth = new FakeAuthService();
    session = new FakeSessionService();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: SessionService, useValue: session },
      ],
    });
  });

  describe('onboardedGuard', () => {
    it('sends a signed-out visitor to sign in, remembering where they were going', async () => {
      auth.signedIn = false;

      expect(destination(await run(onboardedGuard, '/settings/teams'))).toBe(
        '/sign-in?next=%2Fsettings%2Fteams',
      );
    });

    it('sends somebody without a profile to the wizard', async () => {
      session.user = { profileCompleted: false, organisations: [] };

      expect(destination(await run(onboardedGuard))).toBe('/welcome/profile');
    });

    it('sends somebody with a profile but no organisation to the organisation step', async () => {
      session.user = { profileCompleted: true, organisations: [] };

      expect(destination(await run(onboardedGuard))).toBe('/welcome/organisation');
    });

    it('lets a fully set-up clinician through', async () => {
      session.user = {
        profileCompleted: true,
        organisations: [{ orgId: 'a', name: 'Ward', orgRole: 'ORG_MEMBER' }],
      };

      expect(await run(onboardedGuard)).toBe(true);
    });
  });

  describe('onboardingGuard', () => {
    it('keeps somebody who is already set up out of the wizard', async () => {
      session.user = {
        profileCompleted: true,
        organisations: [{ orgId: 'a', name: 'Ward', orgRole: 'ORG_MEMBER' }],
      };

      expect(destination(await run(onboardingGuard, '/welcome/profile'))).toBe('/dashboard');
    });

    it('refuses to skip ahead to the organisation step', async () => {
      session.user = { profileCompleted: false, organisations: [] };

      expect(destination(await run(onboardingGuard, '/welcome/organisation'))).toBe(
        '/welcome/profile',
      );
    });
  });

  describe('orgAdminGuard', () => {
    it('does not offer administrator pages to an ordinary member', async () => {
      session.user = {
        profileCompleted: true,
        organisations: [{ orgId: 'a', name: 'Ward', orgRole: 'ORG_MEMBER' }],
      };

      expect(destination(await run(orgAdminGuard, '/settings/teams'))).toBe('/settings/profile');
    });

    it('lets an administrator through', async () => {
      session.user = {
        profileCompleted: true,
        organisations: [{ orgId: 'a', name: 'Ward', orgRole: 'ORG_ADMIN' }],
      };

      expect(await run(orgAdminGuard, '/settings/teams')).toBe(true);
    });
  });
});
