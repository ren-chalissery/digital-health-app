import { Component, inject } from '@angular/core';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'app-dashboard',
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Welcome{{ firstName() ? ', ' + firstName() : '' }}</h1>
        <p class="page__lede">
          Your progress through the Simplicity training package will appear here.
        </p>
      </header>

      <div class="card">
        <div class="empty">
          <p><strong>Nothing to show yet</strong></p>
          <p>Training modules and progress arrive in the next release.</p>
        </div>
      </div>
    </div>
  `,
})
export class Dashboard {
  private readonly session = inject(SessionService);

  protected firstName(): string {
    return this.session.user()?.fullName?.split(' ')[0] ?? '';
  }
}
