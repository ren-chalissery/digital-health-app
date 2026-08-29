import { Component, inject } from '@angular/core';
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
        @if (organisation(); as org) {
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

  protected async signOut(): Promise<void> {
    await this.session.signOut();
    await this.router.navigate(['/sign-in']);
  }
}
