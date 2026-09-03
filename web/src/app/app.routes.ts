import { Routes } from '@angular/router';
import { onboardedGuard, onboardingGuard, orgAdminGuard } from './core/auth/guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  {
    path: 'sign-in',
    loadComponent: () => import('./features/auth/sign-in').then((m) => m.SignIn),
  },
  {
    path: 'sign-up',
    loadComponent: () => import('./features/auth/sign-up').then((m) => m.SignUp),
  },
  {
    path: 'confirm-email',
    loadComponent: () => import('./features/auth/confirm-email').then((m) => m.ConfirmEmail),
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./features/auth/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'privacy',
    loadComponent: () => import('./features/legal/privacy').then((m) => m.Privacy),
  },

  // Reachable signed out: the recipient usually has no account yet.
  {
    path: 'invitations/:token',
    loadComponent: () =>
      import('./features/invitation/accept-invitation').then((m) => m.AcceptInvitation),
  },

  {
    path: 'welcome',
    canActivate: [onboardingGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'profile' },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/onboarding/profile-wizard').then((m) => m.ProfileWizard),
      },
      {
        path: 'organisation',
        loadComponent: () =>
          import('./features/onboarding/organisation-wizard').then((m) => m.OrganisationWizard),
      },
    ],
  },

  {
    path: '',
    canActivate: [onboardedGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'learn',
        loadComponent: () => import('./features/learn/learn').then((m) => m.Learn),
      },
      {
        path: 'learn/:moduleId',
        loadComponent: () =>
          import('./features/learn/module-reader').then((m) => m.ModuleReader),
      },
      {
        path: 'reflect',
        loadComponent: () => import('./features/reflect/reflect').then((m) => m.Reflect),
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings').then((m) => m.Settings),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'profile' },
          {
            path: 'profile',
            loadComponent: () =>
              import('./features/settings/profile-settings').then((m) => m.ProfileSettings),
          },
          {
            path: 'members',
            canActivate: [orgAdminGuard],
            loadComponent: () =>
              import('./features/settings/member-settings').then((m) => m.MemberSettings),
          },
          {
            path: 'teams',
            canActivate: [orgAdminGuard],
            loadComponent: () =>
              import('./features/settings/team-settings').then((m) => m.TeamSettings),
          },
          {
            path: 'invitations',
            canActivate: [orgAdminGuard],
            loadComponent: () =>
              import('./features/settings/invitation-settings').then((m) => m.InvitationSettings),
          },
          {
            path: 'modules',
            canActivate: [orgAdminGuard],
            loadComponent: () =>
              import('./features/settings/module-settings').then((m) => m.ModuleSettings),
          },
        ],
      },
    ],
  },

  { path: '**', redirectTo: 'dashboard' },
];
