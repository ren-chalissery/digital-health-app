import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../session.service';
import { AuthService } from './auth.service';

/** Signed in with Cognito. Nothing more. */
export const signedInGuard: CanActivateFn = async (_route, state) => {
  const router = inject(Router);
  const auth = inject(AuthService);

  if (await auth.isSignedIn()) {
    return true;
  }
  return router.createUrlTree(['/sign-in'], { queryParams: { next: state.url } });
};

/**
 * Signed in, profile filled in, and a member of an organisation. Everything behind the four tabs
 * assumes all three, so the check lives in one place rather than in each feature.
 */
export const onboardedGuard: CanActivateFn = async (route, state) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  const session = inject(SessionService);

  if (!(await auth.isSignedIn())) {
    return router.createUrlTree(['/sign-in'], { queryParams: { next: state.url } });
  }

  await session.ensureLoaded();
  if (!session.profileCompleted()) {
    return router.createUrlTree(['/welcome/profile']);
  }
  if (!session.hasOrganisation()) {
    return router.createUrlTree(['/welcome/organisation']);
  }
  return true;
};

/**
 * The client hides administrator screens from ordinary members, but the server refuses them
 * regardless; this guard is about not offering a page that would only produce a 403.
 */
export const orgAdminGuard: CanActivateFn = async () => {
  // Both are resolved before the first await: injection is only available synchronously.
  const session = inject(SessionService);
  const router = inject(Router);

  await session.ensureLoaded();
  return session.isOrgAdmin() ? true : router.createUrlTree(['/settings/profile']);
};

/** Keeps somebody who is already set up out of the onboarding screens. */
export const onboardingGuard: CanActivateFn = async (_route, state) => {
  const router = inject(Router);
  const session = inject(SessionService);
  const auth = inject(AuthService);

  if (!(await auth.isSignedIn())) {
    return router.createUrlTree(['/sign-in']);
  }

  await session.ensureLoaded();
  const done = session.profileCompleted() && session.hasOrganisation();
  if (done) {
    return router.createUrlTree(['/dashboard']);
  }

  // Within onboarding, the profile comes before the organisation.
  if (state.url.startsWith('/welcome/organisation') && !session.profileCompleted()) {
    return router.createUrlTree(['/welcome/profile']);
  }
  return true;
};
