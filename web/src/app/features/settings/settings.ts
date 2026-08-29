import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'app-settings',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  styleUrl: './settings.scss',
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Settings</h1>
        <p class="page__lede">Your profile, and — if you administer one — your organisation.</p>
      </header>

      <div class="settings">
        <nav class="settings__nav" aria-label="Settings sections">
          <a routerLink="profile" routerLinkActive="settings__link--active">Profile</a>
          @if (isOrgAdmin()) {
            <a routerLink="members" routerLinkActive="settings__link--active">People</a>
            <a routerLink="teams" routerLinkActive="settings__link--active">Teams</a>
            <a routerLink="invitations" routerLinkActive="settings__link--active">Invitations</a>
          }
        </nav>

        <section class="settings__content">
          <router-outlet />
        </section>
      </div>
    </div>
  `,
})
export class Settings {
  protected readonly isOrgAdmin = inject(SessionService).isOrgAdmin;
}
