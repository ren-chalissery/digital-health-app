import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LearningApi } from '../../api/api/learning.service';
import { AttemptResultResponse } from '../../api/model/attempt-result-response';
import { LearnerModuleResponse } from '../../api/model/learner-module-response';
import { MarkedQuestion } from '../../api/model/marked-question';
import { QuizResponse } from '../../api/model/quiz-response';
import { SectionResponse } from '../../api/model/section-response';
import { renderMarkdown } from '../../core/markdown';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';
import { STATUS_LABELS } from './status-labels';

@Component({
  selector: 'app-module-reader',
  imports: [RouterLink],
  styleUrl: './learn.scss',
  template: `
    <div class="page">
      <header class="page__header">
        <a class="button--link" routerLink="/learn">← All modules</a>
        <h1>{{ module()?.title }}</h1>
        @if (module(); as m) {
          <p class="page__lede">{{ m.summary }}</p>
          <span class="badge" [class.badge--success]="m.status === 'COMPLETED'">
            {{ label() }}
          </span>
        }
      </header>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <div class="card"><p>Loading…</p></div>
      } @else if (module(); as m) {
        <div class="card">
          @for (section of m.sections ?? []; track section.sectionId) {
            <section class="section" [id]="'section-' + section.sectionId">
              <div class="card__title">
                <h2>{{ section.title }}</h2>
                @if (isComplete(section)) {
                  <span class="badge badge--success">Read</span>
                }
              </div>

              <div class="prose" [innerHTML]="rendered(section)"></div>

              @if (!isComplete(section)) {
                <button
                  class="button"
                  type="button"
                  [disabled]="saving()"
                  (click)="markRead(section)"
                >
                  {{ saving() ? 'Saving…' : 'Mark as read' }}
                </button>
              }
            </section>
          }
        </div>

        @if (m.hasQuiz) {
          <div class="card">
            <div class="card__title">
              <h2>Check your understanding</h2>
              @if (m.quizPassed) {
                <span class="badge badge--success">Passed</span>
              }
            </div>

            @if (!allSectionsRead()) {
              <p class="field__hint">
                Work through every section first, then the questions unlock.
              </p>
            } @else if (quiz(); as q) {
              <p class="field__hint">
                Every question has to be right. You can try as often as you like, and a wrong
                answer will explain itself.
              </p>

              @for (question of q.questions ?? []; track question.questionId) {
                <fieldset class="question">
                  <legend>{{ question.prompt }}</legend>
                  @for (option of question.options ?? []; track option.optionId) {
                    <label class="row">
                      <input
                        type="radio"
                        [name]="'q-' + question.questionId"
                        [checked]="chosen()[question.questionId!] === option.optionId"
                        (change)="choose(question.questionId!, option.optionId!)"
                      />
                      <span>{{ option.label }}</span>
                    </label>
                  }

                  @if (feedbackFor(question.questionId!); as marked) {
                    <p
                      class="notice"
                      [class.notice--error]="!marked.wasCorrect"
                      [class.notice--success]="marked.wasCorrect"
                    >
                      {{ marked.wasCorrect ? 'Correct.' : 'Not quite.' }}
                      @if (marked.explanation) {
                        {{ marked.explanation }}
                      }
                    </p>
                  }
                </fieldset>
              }

              <div class="row">
                <button class="button" type="button" [disabled]="saving() || !allAnswered(q)" (click)="submitQuiz()">
                  {{ saving() ? 'Checking…' : result() ? 'Try again' : 'Check my answers' }}
                </button>
                @if (result(); as r) {
                  <span class="field__hint">
                    {{ r.correctCount }} of {{ r.questionCount }} correct on attempt
                    {{ r.attemptNumber }}
                  </span>
                }
              </div>
            }
          </div>
        }
      }
    </div>
  `,
})
export class ModuleReader implements OnInit {
  private readonly api = inject(LearningApi);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly module = signal<LearnerModuleResponse | null>(null);

  protected readonly label = computed(() => STATUS_LABELS[this.module()?.status ?? 'NOT_STARTED']);
  protected readonly quiz = signal<QuizResponse | null>(null);
  protected readonly result = signal<AttemptResultResponse | null>(null);
  protected readonly chosen = signal<Record<string, string>>({});

  private readonly completed = computed(() => new Set(this.module()?.completedSectionIds ?? []));

  protected readonly allSectionsRead = computed(() => {
    const sections = this.module()?.sections ?? [];
    return sections.length > 0 && sections.every((section) => this.isComplete(section));
  });

  async ngOnInit(): Promise<void> {
    const moduleId = this.route.snapshot.paramMap.get('moduleId');
    const orgId = this.session.activeOrganisation()?.orgId;
    if (!moduleId || !orgId) {
      this.loading.set(false);
      return;
    }
    try {
      this.module.set(await firstValueFrom(this.api.readModule(orgId, moduleId)));
      if (this.module()?.hasQuiz) {
        this.quiz.set(await firstValueFrom(this.api.getQuiz(orgId, moduleId)));
      }
      this.scrollToFirstUnread();
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not open this module.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected isComplete(section: SectionResponse): boolean {
    return this.completed().has(section.sectionId ?? '');
  }

  /**
   * The body is Markdown rendered by our own escaping renderer, which emits only tags it generates
   * itself. Nothing an author wrote survives as markup, so this is trusted having been built here
   * rather than having arrived from the server.
   */
  protected rendered(section: SectionResponse): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(renderMarkdown(section.body ?? ''));
  }

  protected async markRead(section: SectionResponse): Promise<void> {
    const orgId = this.session.activeOrganisation()?.orgId;
    if (!orgId || !section.sectionId || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      // The response carries the recomputed status, so finishing the last section shows as
      // complete without a second call.
      this.module.set(await firstValueFrom(this.api.completeSection(orgId, section.sectionId)));
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not record your progress.'));
    } finally {
      this.saving.set(false);
    }
  }

  protected choose(questionId: string, optionId: string): void {
    this.chosen.update((current) => ({ ...current, [questionId]: optionId }));
  }

  protected allAnswered(quiz: QuizResponse): boolean {
    const answers = this.chosen();
    return (quiz.questions ?? []).every((question) => answers[question.questionId ?? '']);
  }

  protected feedbackFor(questionId: string): MarkedQuestion | undefined {
    return (this.result()?.questions ?? []).find((marked) => marked.questionId === questionId);
  }

  protected async submitQuiz(): Promise<void> {
    const orgId = this.session.activeOrganisation()?.orgId;
    const moduleId = this.module()?.moduleId;
    const quiz = this.quiz();
    if (!orgId || !moduleId || !quiz || this.saving()) {
      return;
    }

    // A second press after a result means "try again", so the previous marking is cleared rather
    // than left beside answers it no longer describes.
    if (this.result()) {
      this.result.set(null);
      this.chosen.set({});
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    try {
      const answers = Object.entries(this.chosen()).map(([questionId, optionId]) => ({
        questionId,
        optionId,
      }));
      this.result.set(await firstValueFrom(this.api.submitQuizAttempt(orgId, moduleId, { answers })));
      // Passing may have completed the module, which only the module view knows about.
      this.module.set(await firstValueFrom(this.api.readModule(orgId, moduleId)));
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not check your answers.'));
    } finally {
      this.saving.set(false);
    }
  }

  /** Resuming matters: a module is several sections long and nobody finishes one in a sitting. */
  private scrollToFirstUnread(): void {
    const next = (this.module()?.sections ?? []).find((section) => !this.isComplete(section));
    if (!next?.sectionId) {
      return;
    }
    setTimeout(() => {
      document
        .getElementById('section-' + next.sectionId)
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }
}
