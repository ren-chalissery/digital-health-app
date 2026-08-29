import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { OrganisationsApi } from '../../api/api/organisations.service';
import { TeamsApi } from '../../api/api/teams.service';
import { AddTeamMemberRequest } from '../../api/model/add-team-member-request';
import { OrgMemberResponse } from '../../api/model/org-member-response';
import { TeamMemberDetailResponse } from '../../api/model/team-member-detail-response';
import { TeamResponse } from '../../api/model/team-response';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';

type TeamRole = AddTeamMemberRequest['teamRole'];

@Component({
  selector: 'app-team-settings',
  imports: [ReactiveFormsModule],
  template: `
    <div class="card">
      <div class="card__title">
        <h2>Teams</h2>
        <button class="button button--secondary" type="button" (click)="toggleCreate()">
          {{ creating() ? 'Cancel' : 'New team' }}
        </button>
      </div>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }

      @if (creating()) {
        <form class="stack" [formGroup]="createForm" (ngSubmit)="create()">
          <div class="field">
            <label for="teamName">Team name</label>
            <input id="teamName" formControlName="name" />
          </div>
          <div class="field">
            <label for="teamDescription">Description <span class="field__hint">(optional)</span></label>
            <input id="teamDescription" formControlName="description" />
          </div>
          <div class="row">
            <button class="button" type="submit" [disabled]="busy() || createForm.invalid">
              {{ busy() ? 'Creating…' : 'Create team' }}
            </button>
          </div>
        </form>
      }

      @if (loading()) {
        <p>Loading teams…</p>
      } @else if (teams().length === 0) {
        <div class="empty">
          <p><strong>No teams yet</strong></p>
          <p>Teams group the colleagues who work together on a ward or service.</p>
        </div>
      } @else {
        <table class="table">
          <thead>
            <tr>
              <th>Team</th>
              <th class="numeric">People</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (team of teams(); track team.id) {
              <tr>
                <td>
                  <strong>{{ team.name }}</strong>
                  @if (team.description) {
                    <div class="field__hint">{{ team.description }}</div>
                  }
                </td>
                <td class="numeric">{{ team.memberCount ?? 0 }}</td>
                <td class="numeric">
                  <button class="button--link" type="button" (click)="openTeam(team)">
                    {{ selectedTeam()?.id === team.id ? 'Close' : 'Manage' }}
                  </button>
                  <button class="button button--danger" type="button" (click)="remove(team)">
                    Delete
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>

    @if (selectedTeam(); as team) {
      <div class="card">
        <div class="card__title"><h2>{{ team.name }}</h2></div>

        <form class="row" [formGroup]="addMemberForm" (ngSubmit)="addMember(team)">
          <div class="field spacer">
            <label for="member">Add somebody from your organisation</label>
            <select id="member" formControlName="userId">
              <option value="" disabled>Choose a colleague</option>
              @for (candidate of addableMembers(); track candidate.userId) {
                <option [value]="candidate.userId">
                  {{ candidate.fullName || candidate.email }}
                </option>
              }
            </select>
          </div>
          <div class="field">
            <label for="teamRole">Role</label>
            <select id="teamRole" formControlName="teamRole">
              <option value="TEAM_MEMBER">Member</option>
              <option value="TEAM_ADMIN">Team administrator</option>
            </select>
          </div>
          <button class="button" type="submit" [disabled]="busy() || addMemberForm.invalid">
            Add
          </button>
        </form>

        @if (members().length === 0) {
          <div class="empty"><p>Nobody is in this team yet.</p></div>
        } @else {
          <table class="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Role in team</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (member of members(); track member.userId) {
                <tr>
                  <td>
                    <strong>{{ member.fullName || member.email }}</strong>
                    <div class="field__hint">{{ member.email }}</div>
                  </td>
                  <td>
                    <span class="badge">{{
                      member.teamRole === 'TEAM_ADMIN' ? 'Team administrator' : 'Member'
                    }}</span>
                  </td>
                  <td class="numeric">
                    <button
                      class="button button--danger"
                      type="button"
                      (click)="removeMember(team, member)"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    }
  `,
})
export class TeamSettings implements OnInit {
  private readonly teamsApi = inject(TeamsApi);
  private readonly orgApi = inject(OrganisationsApi);
  private readonly session = inject(SessionService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly creating = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly teams = signal<TeamResponse[]>([]);
  protected readonly orgMembers = signal<OrgMemberResponse[]>([]);
  protected readonly selectedTeam = signal<TeamResponse | null>(null);
  protected readonly members = signal<TeamMemberDetailResponse[]>([]);

  protected readonly createForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
  });

  protected readonly addMemberForm = this.formBuilder.nonNullable.group({
    userId: ['', Validators.required],
    teamRole: ['TEAM_MEMBER' as TeamRole, Validators.required],
  });

  private get orgId(): string {
    return this.session.activeOrganisation()?.orgId ?? '';
  }

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected addableMembers(): OrgMemberResponse[] {
    const already = new Set(this.members().map((member) => member.userId));
    return this.orgMembers().filter((member) => !already.has(member.userId));
  }

  protected toggleCreate(): void {
    this.creating.update((open) => !open);
    this.createForm.reset();
  }

  protected async create(): Promise<void> {
    if (this.createForm.invalid || this.busy()) {
      return;
    }
    const { name, description } = this.createForm.getRawValue();
    await this.run(async () => {
      await firstValueFrom(
        this.teamsApi.createTeam(this.orgId, {
          name: name.trim(),
          description: description.trim() || undefined,
        }),
      );
      this.creating.set(false);
      this.createForm.reset();
      await this.load();
    }, 'Could not create the team.');
  }

  protected async remove(team: TeamResponse): Promise<void> {
    if (!confirm(`Delete ${team.name}? Its members stay in the organisation.`)) {
      return;
    }
    await this.run(async () => {
      await firstValueFrom(this.teamsApi.deleteTeam(this.orgId, team.id!));
      if (this.selectedTeam()?.id === team.id) {
        this.selectedTeam.set(null);
      }
      await this.load();
    }, 'Could not delete the team.');
  }

  protected async openTeam(team: TeamResponse): Promise<void> {
    if (this.selectedTeam()?.id === team.id) {
      this.selectedTeam.set(null);
      return;
    }
    this.selectedTeam.set(team);
    this.addMemberForm.reset({ userId: '', teamRole: 'TEAM_MEMBER' });
    await this.run(async () => {
      this.members.set(await firstValueFrom(this.teamsApi.listTeamMembers(this.orgId, team.id!)));
    }, 'Could not load the team.');
  }

  protected async addMember(team: TeamResponse): Promise<void> {
    if (this.addMemberForm.invalid || this.busy()) {
      return;
    }
    const { userId, teamRole } = this.addMemberForm.getRawValue();
    await this.run(async () => {
      await firstValueFrom(this.teamsApi.addTeamMember(this.orgId, team.id!, { userId, teamRole }));
      this.addMemberForm.controls.userId.setValue('');
      await this.refreshTeam(team);
    }, 'Could not add that person to the team.');
  }

  protected async removeMember(
    team: TeamResponse,
    member: TeamMemberDetailResponse,
  ): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.teamsApi.removeTeamMember(this.orgId, team.id!, member.userId!));
      await this.refreshTeam(team);
    }, 'Could not remove that person.');
  }

  private async refreshTeam(team: TeamResponse): Promise<void> {
    this.members.set(await firstValueFrom(this.teamsApi.listTeamMembers(this.orgId, team.id!)));
    this.teams.set(await firstValueFrom(this.teamsApi.listTeams(this.orgId)));
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [teams, members] = await Promise.all([
        firstValueFrom(this.teamsApi.listTeams(this.orgId)),
        firstValueFrom(this.orgApi.listOrganisationMembers(this.orgId)),
      ]);
      this.teams.set(teams);
      this.orgMembers.set(members);
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  private async run(action: () => Promise<void>, fallback: string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(problemMessage(error, fallback));
    } finally {
      this.busy.set(false);
    }
  }
}
