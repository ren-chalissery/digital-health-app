import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AssistantApi } from '../../api/api/assistant.service';
import { LearningApi } from '../../api/api/learning.service';
import { AnswerResponse } from '../../api/model/answer-response';
import { AssignedModuleResponse } from '../../api/model/assigned-module-response';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';
import { STATUS_LABELS } from './status-labels';

@Component({
  selector: 'app-learn',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <header class="page__header">
        <h1>Learn</h1>
        <p class="page__lede">
          Training modules covering how to introduce and support Simplicity with the people you
          work with.
        </p>
      </header>

      <div class="card">
        <div class="card__title"><h2>Ask about the training</h2></div>
        <p class="field__hint">
          Answers come from your organisation's published modules and cite where they came from.
          It cannot help with a particular person or a situation you are managing — that belongs
          with your supervisor.
        </p>

        <div class="field">
          <label for="question">Your question</label>
          <input
            id="question"
            [(ngModel)]="question"
            name="question"
            (keyup.enter)="ask()"
            placeholder="What does the training say about…"
          />
        </div>
        <div class="row">
          <button class="button" type="button" [disabled]="asking() || !question.trim()" (click)="ask()">
            {{ asking() ? 'Looking…' : 'Ask' }}
          </button>
        </div>

        @if (answer(); as result) {
          <div class="notice" [class.notice--warning]="!result.answered">
            <p>{{ result.answer }}</p>
            @if ((result.citations ?? []).length > 0) {
              <p class="field__hint">From:</p>
              @for (citation of result.citations; track citation.moduleId) {
                <p class="field__hint">
                  @if (citation.assignedToYou) {
                    <a [routerLink]="['/learn', citation.moduleId]">{{ citation.moduleTitle }}</a>
                  } @else {
                    <!-- Retrieval spans the organisation, so this may be a module they cannot
                         open. Naming it without a link beats sending them to a dead end. -->
                    {{ citation.moduleTitle }} <em>(not assigned to your teams)</em>
                  }
                  @if (citation.sectionTitle) {
                    · {{ citation.sectionTitle }}
                  }
                </p>
              }
            }
          </div>
        }
      </div>

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
  private readonly assistant = inject(AssistantApi);
  private readonly session = inject(SessionService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly modules = signal<AssignedModuleResponse[]>([]);
  protected readonly asking = signal(false);
  protected readonly answer = signal<AnswerResponse | null>(null);
  protected question = '';

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

  protected async ask(): Promise<void> {
    const orgId = this.session.activeOrganisation()?.orgId;
    if (!orgId || !this.question.trim() || this.asking()) {
      return;
    }
    this.asking.set(true);
    this.error.set(null);
    try {
      this.answer.set(
        await firstValueFrom(this.assistant.askAssistant(orgId, { question: this.question })),
      );
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not ask that just now.'));
    } finally {
      this.asking.set(false);
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
