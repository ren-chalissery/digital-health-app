import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LearningApi } from '../../api/api/learning.service';
import { AssignedModuleResponse } from '../../api/model/assigned-module-response';
import { SessionService } from '../../core/session.service';
import { STATUS_LABELS } from '../learn/status-labels';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  styleUrl: '../learn/learn.scss',
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Welcome back{{ firstName() ? ', ' + firstName() : '' }}</h1>
        <p class="page__lede">{{ lede() }}</p>
      </header>

      @if (!loading() && next(); as module) {
        <a class="card card--link" [routerLink]="['/learn', module.moduleId]">
          <div class="card__title">
            <h2>{{ module.title }}</h2>
            <span class="badge badge--accent">{{ label(module) }}</span>
          </div>
          @if (module.summary) {
            <p>{{ module.summary }}</p>
          }
          <p class="field__hint">
            {{ module.completedSectionCount }} of {{ module.sectionCount }} sections
          </p>
        </a>
      }

      @if (!loading() && outstanding().length === 0 && assigned().length > 0) {
        <div class="card">
          <div class="empty">
            <p><strong>You are up to date</strong></p>
            <p>Everything assigned to your teams is complete.</p>
          </div>
        </div>
      }

      @if (!loading() && assigned().length === 0) {
        <div class="card">
          <div class="empty">
            <p><strong>Nothing assigned yet</strong></p>
            <p>Modules reach you through your teams. They will show up here.</p>
          </div>
        </div>
      }
    </div>
  `,
})
export class Dashboard implements OnInit {
  private readonly api = inject(LearningApi);
  private readonly session = inject(SessionService);

  protected readonly loading = signal(true);
  protected readonly assigned = signal<AssignedModuleResponse[]>([]);

  protected readonly outstanding = computed(() =>
    this.assigned().filter((module) => module.status !== 'COMPLETED'),
  );

  /** Whatever is already underway, else the first thing not started. */
  protected readonly next = computed(
    () =>
      this.outstanding().find((module) => module.status === 'IN_PROGRESS') ??
      this.outstanding()[0] ??
      null,
  );

  protected readonly lede = computed(() => {
    if (this.loading()) {
      return 'Loading your training…';
    }
    const count = this.outstanding().length;
    if (this.assigned().length === 0) {
      return 'Nothing has been assigned to your teams yet.';
    }
    return count === 0
      ? 'You have finished everything assigned to you.'
      : `You have ${count} module${count === 1 ? '' : 's'} outstanding.`;
  });

  protected firstName(): string {
    return (this.session.user()?.fullName ?? '').split(' ')[0] ?? '';
  }

  protected label(module: AssignedModuleResponse): string {
    return STATUS_LABELS[module.status ?? 'NOT_STARTED'];
  }

  async ngOnInit(): Promise<void> {
    const orgId = this.session.activeOrganisation()?.orgId;
    if (!orgId) {
      this.loading.set(false);
      return;
    }
    try {
      this.assigned.set(await firstValueFrom(this.api.listAssignedModules(orgId)));
    } catch {
      // The Dashboard is a summary of something Learn shows properly; failing quietly here beats
      // an error banner on the first screen after signing in.
    } finally {
      this.loading.set(false);
    }
  }
}
