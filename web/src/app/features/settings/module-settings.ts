import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ModulesApi } from '../../api/api/modules.service';
import { TeamsApi } from '../../api/api/teams.service';
import { AuthoredModuleResponse } from '../../api/model/authored-module-response';
import { ModuleSummaryResponse } from '../../api/model/module-summary-response';
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
  private readonly session = inject(SessionService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly modules = signal<ModuleSummaryResponse[]>([]);
  protected readonly teams = signal<TeamResponse[]>([]);
  protected readonly editing = signal<AuthoredModuleResponse | null>(null);
  protected readonly sections = signal<SectionInput[]>([]);
  protected readonly assignedTeamIds = signal<string[]>([]);

  protected title = '';
  protected summary = '';
  protected supersedes = false;

  private get orgId(): string {
    return this.session.activeOrganisation()?.orgId ?? '';
  }

  async ngOnInit(): Promise<void> {
    await Promise.all([this.load(), this.loadTeams()]);
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
      this.show(
        await firstValueFrom(
          this.api.replaceModuleSections(this.orgId, moduleId, { sections: this.sections() }),
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
