import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { MediaApi } from '../../api/api/media.service';
import { ModulesApi } from '../../api/api/modules.service';
import { TeamsApi } from '../../api/api/teams.service';
import { AuthoredModuleResponse } from '../../api/model/authored-module-response';
import { ModuleSummaryResponse } from '../../api/model/module-summary-response';
import { MediaAssetResponse } from '../../api/model/media-asset-response';
import { QuestionInput } from '../../api/model/question-input';
import { SectionInput } from '../../api/model/section-input';
import { TeamResponse } from '../../api/model/team-response';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'app-module-settings',
  imports: [FormsModule],
  styleUrl: './module-settings.scss',
  template: `
    <div class="card">
      <div class="card__title">
        <h2>Training modules</h2>
        <button class="button" type="button" (click)="create()" [disabled]="busy()">
          New module
        </button>
      </div>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <p>Loading modules…</p>
      } @else if (modules().length === 0) {
        <p class="field__hint">
          Nothing written yet. A module is a series of sections; teams you assign it to will see it
          in Learn once you publish.
        </p>
      } @else {
        <table class="table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Published</th>
              <th>Teams</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (module of modules(); track module.moduleId) {
              <tr>
                <td>
                  <strong>{{ module.title }}</strong>
                  @if (module.summary) {
                    <div class="field__hint">{{ module.summary }}</div>
                  }
                </td>
                <td>
                  @if (module.publishedVersion) {
                    <span class="badge badge--success">Version {{ module.publishedVersion }}</span>
                  } @else {
                    <span class="badge">Unpublished</span>
                  }
                  @if (module.hasDraft) {
                    <span class="badge badge--accent">Draft</span>
                  }
                </td>
                <td>{{ module.assignedTeamCount }}</td>
                <td class="numeric">
                  <button class="button--link" type="button" (click)="open(module.moduleId!)">
                    Edit
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>

    @if (editing(); as module) {
      <div class="card">
        <div class="card__title">
          <h2>{{ module.title }}</h2>
          <button class="button--link" type="button" (click)="editing.set(null)">Close</button>
        </div>

        @if (!module.draft) {
          <p class="field__hint">
            Learners currently have version {{ module.published?.versionNumber }}. Editing opens a
            new draft, copied from it, so nothing changes for them until you publish.
          </p>
          <button class="button" type="button" [disabled]="busy()" (click)="openDraft()">
            Edit content
          </button>
        } @else {
          <div class="field">
            <label for="title">Title</label>
            <input id="title" [(ngModel)]="title" name="title" />
          </div>
          <div class="field">
            <label for="summary">Summary <span class="field__hint">(optional)</span></label>
            <input id="summary" [(ngModel)]="summary" name="summary" />
          </div>

          <h3>Sections</h3>
          @for (section of sections(); track $index) {
            <div class="section-editor">
              <div class="field">
                <label [for]="'section-title-' + $index">Section {{ $index + 1 }}</label>
                <input
                  [id]="'section-title-' + $index"
                  [(ngModel)]="section.title"
                  [name]="'section-title-' + $index"
                />
              </div>
              <div class="field">
                <label [for]="'section-body-' + $index">
                  Content <span class="field__hint">Markdown</span>
                </label>
                <textarea
                  [id]="'section-body-' + $index"
                  rows="8"
                  [(ngModel)]="section.body"
                  [name]="'section-body-' + $index"
                ></textarea>
              </div>
              <div class="field">
                <label [for]="'section-media-' + $index">
                  Video <span class="field__hint">optional</span>
                </label>
                <select
                  [id]="'section-media-' + $index"
                  [(ngModel)]="section.mediaAssetId"
                  [name]="'section-media-' + $index"
                >
                  <option [ngValue]="undefined">No video</option>
                  @for (asset of readyMedia(); track asset.assetId) {
                    <option [ngValue]="asset.assetId">{{ asset.filename }}</option>
                  }
                </select>
              </div>

              <div class="row">
                <button class="button--link" type="button" (click)="move($index, -1)" [disabled]="$index === 0">
                  Move up
                </button>
                <button
                  class="button--link"
                  type="button"
                  (click)="move($index, 1)"
                  [disabled]="$index === sections().length - 1"
                >
                  Move down
                </button>
                <button class="button--link" type="button" (click)="removeSection($index)">
                  Delete
                </button>
              </div>
            </div>
          }

          <div class="row">
            <button class="button--link" type="button" (click)="addSection()">Add a section</button>
          </div>

          <h3>Questions</h3>
          <p class="field__hint">
            Optional. Where there are questions, a clinician must get every one right before the
            module counts as complete. They may retry as often as they like, so the explanation is
            what does the teaching.
          </p>

          @for (question of questions(); track $index; let qi = $index) {
            <div class="section-editor">
              <div class="field">
                <label [for]="'q-prompt-' + qi">Question {{ qi + 1 }}</label>
                <input [id]="'q-prompt-' + qi" [(ngModel)]="question.prompt" [name]="'q-prompt-' + qi" />
              </div>

              @for (option of question.options; track $index; let oi = $index) {
                <div class="row">
                  <input
                    type="radio"
                    [name]="'q-correct-' + qi"
                    [checked]="option.correct"
                    (change)="markCorrect(qi, oi)"
                  />
                  <input
                    class="grow"
                    [(ngModel)]="option.label"
                    [name]="'q-option-' + qi + '-' + oi"
                    placeholder="Answer"
                  />
                  <button class="button--link" type="button" (click)="removeOption(qi, oi)">
                    Remove
                  </button>
                </div>
              }
              <div class="row">
                <button class="button--link" type="button" (click)="addOption(qi)">
                  Add an answer
                </button>
              </div>

              <div class="field">
                <label [for]="'q-explanation-' + qi">
                  Explanation <span class="field__hint">shown after they answer</span>
                </label>
                <input
                  [id]="'q-explanation-' + qi"
                  [(ngModel)]="question.explanation"
                  [name]="'q-explanation-' + qi"
                />
              </div>

              <div class="row">
                <button class="button--link" type="button" (click)="removeQuestion(qi)">
                  Delete question
                </button>
              </div>
            </div>
          }

          <div class="row">
            <button class="button--link" type="button" (click)="addQuestion()">
              Add a question
            </button>
          </div>

          <div class="row">
            <button class="button" type="button" [disabled]="busy()" (click)="saveDraft()">
              {{ busy() ? 'Saving…' : 'Save draft' }}
            </button>
            @if (saved()) {
              <span class="field__hint">Saved</span>
            }
          </div>

          <h3>Publish</h3>
          <label class="row">
            <input type="checkbox" [(ngModel)]="supersedes" name="supersedes" />
            <span>
              This is a substantive change. Anyone who completed an earlier version will be asked to
              work through it again. Leave unticked for corrections and typos.
            </span>
          </label>
          <div class="row">
            <button class="button" type="button" [disabled]="busy()" (click)="publish()">
              Publish
            </button>
          </div>
        }

        <h3>Video library</h3>
        <p class="field__hint">
          MP4 up to 500MB. Uploads go straight to storage and are converted for playback, which
          takes a few minutes; a section can use one once it is ready.
        </p>

        @if (uploadError()) {
          <p class="notice notice--error" role="alert">{{ uploadError() }}</p>
        }

        <div class="row">
          <input type="file" accept="video/mp4,video/quicktime,video/webm" (change)="upload($event)" />
          @if (uploadProgress() !== null) {
            <span class="field__hint">Uploading… {{ uploadProgress() }}%</span>
          }
        </div>

        @for (asset of media(); track asset.assetId) {
          <div class="row">
            <span>{{ asset.filename }}</span>
            <span
              class="badge"
              [class.badge--success]="asset.status === 'READY'"
              [class.badge--accent]="asset.status === 'PROCESSING'"
            >
              {{ mediaLabel(asset) }}
            </span>
            @if (asset.hasCaptions) {
              <span class="badge badge--success">Captions</span>
              <button class="button--link" type="button" (click)="removeCaptions(asset.assetId!)">
                Remove captions
              </button>
            } @else if (asset.status === 'READY') {
              <label class="button--link">
                Add captions
                <input
                  type="file"
                  accept=".vtt,text/vtt"
                  hidden
                  (change)="addCaptions(asset.assetId!, $event)"
                />
              </label>
            }
            <button class="button--link" type="button" (click)="deleteMedia(asset.assetId!)">
              Delete
            </button>
          </div>
        }

        <p class="field__hint">
          Captions are a WebVTT file. Without them the video is unusable to anyone who is deaf or
          hard of hearing, and to anyone watching somewhere they cannot play sound.
        </p>

        <h3>Assigned teams</h3>
        <p class="field__hint">Modules reach clinicians through their teams.</p>
        @for (team of teams(); track team.id) {
          <label class="row">
            <input
              type="checkbox"
              [checked]="isAssigned(team.id!)"
              (change)="toggleTeam(team.id!)"
            />
            <span>{{ team.name }}</span>
          </label>
        }
        <div class="row">
          <button class="button" type="button" [disabled]="busy()" (click)="saveTeams()">
            Save assignment
          </button>
        </div>
      </div>
    }
  `,
})
export class ModuleSettings implements OnInit {
  private readonly api = inject(ModulesApi);
  private readonly teamsApi = inject(TeamsApi);
  private readonly mediaApi = inject(MediaApi);
  private readonly session = inject(SessionService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly modules = signal<ModuleSummaryResponse[]>([]);
  protected readonly teams = signal<TeamResponse[]>([]);
  protected readonly editing = signal<AuthoredModuleResponse | null>(null);
  protected readonly sections = signal<SectionInput[]>([]);
  protected readonly questions = signal<QuestionInput[]>([]);
  protected readonly assignedTeamIds = signal<string[]>([]);
  protected readonly media = signal<MediaAssetResponse[]>([]);
  protected readonly uploadProgress = signal<number | null>(null);
  protected readonly uploadError = signal<string | null>(null);

  protected readonly readyMedia = computed(() =>
    this.media().filter((asset) => asset.status === 'READY'),
  );

  protected title = '';
  protected summary = '';
  protected supersedes = false;

  private get orgId(): string {
    return this.session.activeOrganisation()?.orgId ?? '';
  }

  async ngOnInit(): Promise<void> {
    await Promise.all([this.load(), this.loadTeams(), this.loadMedia()]);
    this.loading.set(false);
  }

  protected async create(): Promise<void> {
    await this.run(async () => {
      const created = await firstValueFrom(
        this.api.createModule(this.orgId, { title: 'Untitled module' }),
      );
      await this.load();
      this.show(created);
    }, 'Could not create the module.');
  }

  protected async open(moduleId: string): Promise<void> {
    await this.run(async () => {
      this.show(await firstValueFrom(this.api.getModule(this.orgId, moduleId)));
    }, 'Could not open the module.');
  }

  protected async openDraft(): Promise<void> {
    const moduleId = this.editing()?.moduleId;
    if (!moduleId) {
      return;
    }
    await this.run(async () => {
      this.show(await firstValueFrom(this.api.openModuleDraft(this.orgId, moduleId)));
      await this.load();
    }, 'Could not open a draft.');
  }

  protected async saveDraft(): Promise<void> {
    const moduleId = this.editing()?.moduleId;
    if (!moduleId) {
      return;
    }
    this.saved.set(false);
    await this.run(async () => {
      await firstValueFrom(this.api.updateModule(this.orgId, moduleId, { title: this.title, summary: this.summary }));
      await firstValueFrom(
        this.api.replaceModuleSections(this.orgId, moduleId, { sections: this.sections() }),
      );
      this.show(
        await firstValueFrom(
          this.api.replaceModuleQuiz(this.orgId, moduleId, { questions: this.questions() }),
        ),
      );
      await this.load();
      this.saved.set(true);
    }, 'Could not save the draft.');
  }

  protected async publish(): Promise<void> {
    const moduleId = this.editing()?.moduleId;
    if (!moduleId) {
      return;
    }
    await this.run(async () => {
      // Saving first means the publish always covers what is on screen, rather than whatever was
      // last saved.
      await firstValueFrom(
        this.api.replaceModuleSections(this.orgId, moduleId, { sections: this.sections() }),
      );
      await firstValueFrom(
        this.api.replaceModuleQuiz(this.orgId, moduleId, { questions: this.questions() }),
      );
      this.show(
        await firstValueFrom(
          this.api.publishModule(this.orgId, moduleId, { supersedesCompletions: this.supersedes }),
        ),
      );
      await this.load();
      this.supersedes = false;
    }, 'Could not publish.');
  }

  protected async saveTeams(): Promise<void> {
    const moduleId = this.editing()?.moduleId;
    if (!moduleId) {
      return;
    }
    await this.run(async () => {
      this.show(
        await firstValueFrom(
          this.api.assignModuleToTeams(this.orgId, moduleId, { teamIds: this.assignedTeamIds() }),
        ),
      );
      await this.load();
    }, 'Could not save the assignment.');
  }

  protected mediaLabel(asset: MediaAssetResponse): string {
    if (asset.status === 'FAILED') {
      return asset.failureReason ? 'Failed: ' + asset.failureReason : 'Failed';
    }
    return asset.status === 'READY' ? 'Ready' : 'Converting…';
  }

  /**
   * Registers the file, PUTs it straight to storage, then tells the API it landed. The bytes never
   * pass through the application.
   */
  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.uploadError.set(null);
    this.uploadProgress.set(0);
    try {
      const target = await firstValueFrom(
        this.mediaApi.registerUpload(this.orgId, {
          filename: file.name,
          contentType: file.type,
          sizeBytes: file.size,
        }),
      );
      await this.putToStorage(target.uploadUrl!, file);
      await firstValueFrom(this.mediaApi.completeUpload(this.orgId, target.assetId!));
      await this.loadMedia();
    } catch (error) {
      this.uploadError.set(problemMessage(error, 'Could not upload that video.'));
    } finally {
      this.uploadProgress.set(null);
      input.value = '';
    }
  }

  // XMLHttpRequest rather than fetch, because only it reports upload progress, and a 500MB upload
  // with no feedback looks like a hung page.
  private putToStorage(url: string, file: File): Promise<void> {
    return new Promise((resolve, reject) => {
      const request = new XMLHttpRequest();
      request.open('PUT', url);
      request.setRequestHeader('Content-Type', file.type);
      request.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          this.uploadProgress.set(Math.round((event.loaded / event.total) * 100));
        }
      };
      request.onload = () =>
        request.status >= 200 && request.status < 300
          ? resolve()
          : reject(new Error('Storage rejected the upload (' + request.status + ')'));
      request.onerror = () => reject(new Error('The upload failed'));
      request.send(file);
    });
  }

  protected async addCaptions(assetId: string, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    await this.run(async () => {
      // Sent as the body rather than presigned: a caption file is kilobytes.
      await firstValueFrom(this.mediaApi.setCaptions(this.orgId, assetId, await file.text()));
      await this.loadMedia();
    }, 'Could not add those captions.');
    input.value = '';
  }

  protected async removeCaptions(assetId: string): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.mediaApi.removeCaptions(this.orgId, assetId));
      await this.loadMedia();
    }, 'Could not remove those captions.');
  }

  protected async deleteMedia(assetId: string): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.mediaApi.deleteMedia(this.orgId, assetId));
      await this.loadMedia();
      // A section may have just lost its video.
      const moduleId = this.editing()?.moduleId;
      if (moduleId) {
        this.show(await firstValueFrom(this.api.getModule(this.orgId, moduleId)));
      }
    }, 'Could not delete that video.');
  }

  private async loadMedia(): Promise<void> {
    this.media.set(await firstValueFrom(this.mediaApi.listMedia(this.orgId)));
  }

  protected addQuestion(): void {
    this.questions.update((current) => [
      ...current,
      {
        prompt: 'New question',
        explanation: '',
        // Two options, because a question with fewer cannot be published.
        options: [
          { label: '', correct: true },
          { label: '', correct: false },
        ],
      },
    ]);
  }

  protected removeQuestion(index: number): void {
    this.questions.update((current) => current.filter((_, i) => i !== index));
  }

  protected addOption(questionIndex: number): void {
    this.questions.update((current) =>
      current.map((question, i) =>
        i === questionIndex
          ? { ...question, options: [...question.options, { label: '', correct: false }] }
          : question,
      ),
    );
  }

  protected removeOption(questionIndex: number, optionIndex: number): void {
    this.questions.update((current) =>
      current.map((question, i) =>
        i === questionIndex
          ? { ...question, options: question.options.filter((_, o) => o !== optionIndex) }
          : question,
      ),
    );
  }

  /** Exactly one correct answer per question, which is what publishing will insist on. */
  protected markCorrect(questionIndex: number, optionIndex: number): void {
    this.questions.update((current) =>
      current.map((question, i) =>
        i === questionIndex
          ? {
              ...question,
              options: question.options.map((option, o) => ({ ...option, correct: o === optionIndex })),
            }
          : question,
      ),
    );
  }

  protected addSection(): void {
    this.sections.update((current) => [...current, { title: 'New section', body: '' }]);
  }

  protected removeSection(index: number): void {
    this.sections.update((current) => current.filter((_, i) => i !== index));
  }

  protected move(index: number, by: number): void {
    this.sections.update((current) => {
      const next = [...current];
      const [moved] = next.splice(index, 1);
      next.splice(index + by, 0, moved);
      return next;
    });
  }

  protected isAssigned(teamId: string): boolean {
    return this.assignedTeamIds().includes(teamId);
  }

  protected toggleTeam(teamId: string): void {
    this.assignedTeamIds.update((current) =>
      current.includes(teamId) ? current.filter((id) => id !== teamId) : [...current, teamId],
    );
  }

  private show(module: AuthoredModuleResponse): void {
    this.editing.set(module);
    this.title = module.title ?? '';
    this.summary = module.summary ?? '';
    this.assignedTeamIds.set([...(module.assignedTeamIds ?? [])]);
    this.sections.set(
      (module.draft?.sections ?? []).map((section) => ({
        title: section.title ?? '',
        body: section.body ?? '',
        mediaAssetId: section.mediaAssetId,
      })),
    );
    this.questions.set(
      (module.draft?.questions ?? []).map((question) => ({
        prompt: question.prompt ?? '',
        explanation: question.explanation ?? '',
        options: (question.options ?? []).map((option) => ({
          label: option.label ?? '',
          correct: option.correct ?? false,
        })),
      })),
    );
  }

  private async load(): Promise<void> {
    this.modules.set(await firstValueFrom(this.api.listModules(this.orgId)));
  }

  private async loadTeams(): Promise<void> {
    this.teams.set(await firstValueFrom(this.teamsApi.listTeams(this.orgId)));
  }

  private async run(work: () => Promise<void>, message: string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await work();
    } catch (error) {
      this.error.set(problemMessage(error, message));
    } finally {
      this.busy.set(false);
    }
  }
}
