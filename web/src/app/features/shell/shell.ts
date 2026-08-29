import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../../core/session.service';

const TABS = [
  { path: '/dashboard', label: 'Dashboard' },
  { path: '/learn', label: 'Learn' },
  { path: '/reflect', label: 'Reflect' },
  { path: '/settings', label: 'Settings' },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  styleUrl: './shell.scss',
  template: `
    <header class="masthead">
      <div class="masthead__inner">
        <a class="masthead__brand" routerLink="/dashboard">Simplicity training</a>
        <span class="spacer"></span>
        @if (canSwitch()) {
          <label class="masthead__org-switch">
            <span class="visually-hidden">Current organisation</span>
            <select
              [value]="organisation()?.orgId ?? ''"
              [disabled]="switching()"
              (change)="switchTo($event)"
            >
              @for (org of organisations(); track org.orgId) {
                <option [value]="org.orgId">{{ org.name }}</option>
              }
            </select>
          </label>
        } @else if (organisation(); as org) {
          <span class="masthead__org">{{ org.name }}</span>
        }
        <button class="button--link" type="button" (click)="signOut()">Sign out</button>
      </div>

      <nav class="tabs" aria-label="Main">
        <div class="tabs__inner">
          @for (tab of tabs; track tab.path) {
            <a
              class="tabs__tab"
              [routerLink]="tab.path"
              routerLinkActive="tabs__tab--active"
              #link="routerLinkActive"
              [attr.aria-current]="link.isActive ? 'page' : null"
            >
              {{ tab.label }}
            </a>
          }
        </div>
      </nav>
    </header>

    <main>
      <router-outlet />
    </main>
  `,
})
export class Shell {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly tabs = TABS;
  protected readonly organisation = this.session.activeOrganisation;
  protected readonly organisations = this.session.organisations;
  protected readonly canSwitch = this.session.canSwitchOrganisation;
  protected readonly switching = signal(false);

  protected async switchTo(event: Event): Promise<void> {
    const orgId = (event.target as HTMLSelectElement).value;
    if (!orgId || orgId === this.organisation()?.orgId) {
      return;
    }
    this.switching.set(true);
    try {
      await this.session.setActiveOrganisation(orgId);
      // Back to the dashboard rather than staying put: the screen underneath may be showing the
      // previous organisation's teams, and several of them are admin-only in one and not the other.
      await this.router.navigateByUrl('/dashboard');
    } finally {
      this.switching.set(false);
    }
  }

  protected async signOut(): Promise<void> {
    await this.session.signOut();
    await this.router.navigate(['/sign-in']);
  }
}
