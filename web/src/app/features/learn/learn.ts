import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LearningApi } from '../../api/api/learning.service';
import { AssignedModuleResponse } from '../../api/model/assigned-module-response';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';
import { STATUS_LABELS } from './status-labels';

@Component({
  selector: 'app-learn',
  imports: [RouterLink],
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Learn</h1>
        <p class="page__lede">
          Training modules covering how to introduce and support Simplicity with the people you
          work with.
        </p>
      </header>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <div class="card"><p>Loading your modules…</p></div>
      } @else if (modules().length === 0) {
        <div class="card">
          <div class="empty">
            <p><strong>Nothing assigned yet</strong></p>
            <p>
              Modules reach you through your teams. When an administrator assigns one, it appears
              here.
            </p>
          </div>
        </div>
      } @else {
        @for (module of modules(); track module.moduleId) {
          <a class="card card--link" [routerLink]="['/learn', module.moduleId]">
            <div class="card__title">
              <h2>{{ module.title }}</h2>
              <span class="badge" [class]="badgeClass(module)">{{ label(module) }}</span>
            </div>
            @if (module.summary) {
              <p>{{ module.summary }}</p>
            }
            <p class="field__hint">
              {{ module.completedSectionCount }} of {{ module.sectionCount }} sections
            </p>
            <div class="progress" role="presentation">
              <div class="progress__bar" [style.width.%]="percent(module)"></div>
            </div>
          </a>
        }
      }
    </div>
  `,
  styleUrl: './learn.scss',
})
export class Learn implements OnInit {
  private readonly api = inject(LearningApi);
  private readonly session = inject(SessionService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly modules = signal<AssignedModuleResponse[]>([]);

  async ngOnInit(): Promise<void> {
    const orgId = this.session.activeOrganisation()?.orgId;
    if (!orgId) {
      this.loading.set(false);
      return;
    }
    try {
      this.modules.set(await firstValueFrom(this.api.listAssignedModules(orgId)));
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not load your modules.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected label(module: AssignedModuleResponse): string {
    return STATUS_LABELS[module.status ?? 'NOT_STARTED'];
  }

  protected badgeClass(module: AssignedModuleResponse): string {
    if (module.status === 'COMPLETED') {
      return 'badge--success';
    }
    return module.status === 'NEEDS_REDOING' ? 'badge--accent' : '';
  }

  protected percent(module: AssignedModuleResponse): number {
    const total = module.sectionCount ?? 0;
    return total === 0 ? 0 : Math.round(((module.completedSectionCount ?? 0) / total) * 100);
  }
}
