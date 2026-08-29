import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { InvitationsApi } from '../../api/api/invitations.service';
import { TeamsApi } from '../../api/api/teams.service';
import { CreateInvitationRequest } from '../../api/model/create-invitation-request';
import { InvitationResponse } from '../../api/model/invitation-response';
import { TeamResponse } from '../../api/model/team-response';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';

type OrgRole = CreateInvitationRequest['orgRole'];
type TeamRole = NonNullable<CreateInvitationRequest['teamRole']>;

@Component({
  selector: 'app-invitation-settings',
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    <div class="card">
      <div class="card__title"><h2>Invite a colleague</h2></div>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }
      @if (sentTo()) {
        <p class="notice notice--success" role="status">
          An invitation is on its way to {{ sentTo() }}.
        </p>
      }

      <form class="stack" [formGroup]="form" (ngSubmit)="invite()">
        <div class="field">
          <label for="inviteEmail">Email address</label>
          <input id="inviteEmail" type="email" formControlName="email" />
          <p class="field__hint">
            Inviting an address again withdraws the previous link and sends a new one.
          </p>
        </div>

        <div class="row">
          <div class="field spacer">
            <label for="orgRole">Role in the organisation</label>
            <select id="orgRole" formControlName="orgRole">
              <option value="ORG_MEMBER">Member</option>
              <option value="ORG_ADMIN">Administrator</option>
            </select>
          </div>

          <div class="field spacer">
            <label for="inviteTeam">Team <span class="field__hint">(optional)</span></label>
            <select id="inviteTeam" formControlName="teamId">
              <option value="">No team</option>
              @for (team of teams(); track team.id) {
                <option [value]="team.id">{{ team.name }}</option>
              }
            </select>
          </div>

          @if (form.controls.teamId.value) {
            <div class="field spacer">
              <label for="inviteTeamRole">Role in the team</label>
              <select id="inviteTeamRole" formControlName="teamRole">
                <option value="TEAM_MEMBER">Member</option>
                <option value="TEAM_ADMIN">Team administrator</option>
              </select>
            </div>
          }
        </div>

        <div class="row">
          <button class="button" type="submit" [disabled]="busy() || form.invalid">
            {{ busy() ? 'Sending…' : 'Send invitation' }}
          </button>
        </div>
      </form>
    </div>

    <div class="card">
      <div class="card__title"><h2>Invitations</h2></div>

      @if (loading()) {
        <p>Loading invitations…</p>
      } @else if (invitations().length === 0) {
        <div class="empty"><p>No invitations have been sent yet.</p></div>
      } @else {
        <table class="table">
          <thead>
            <tr>
              <th>Email</th>
              <th>Invited as</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (invitation of invitations(); track invitation.id) {
              <tr>
                <td>
                  <strong>{{ invitation.email }}</strong>
                  @if (invitation.teamName) {
                    <div class="field__hint">Team: {{ invitation.teamName }}</div>
                  }
                </td>
                <td>{{ invitation.orgRole === 'ORG_ADMIN' ? 'Administrator' : 'Member' }}</td>
                <td>
                  <span class="badge" [class]="statusClass(invitation)">
                    {{ label(invitation) }}
                  </span>
                  @if (invitation.status === 'PENDING') {
                    <div class="field__hint">
                      Expires {{ invitation.expiresAt | date: 'd MMM y' }}
                    </div>
                  }
                </td>
                <td class="numeric">
                  @if (invitation.status === 'PENDING') {
                    <button
                      class="button button--danger"
                      type="button"
                      (click)="revoke(invitation)"
                    >
                      Withdraw
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
})
export class InvitationSettings implements OnInit {
  private readonly api = inject(InvitationsApi);
  private readonly teamsApi = inject(TeamsApi);
  private readonly session = inject(SessionService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sentTo = signal<string | null>(null);
  protected readonly invitations = signal<InvitationResponse[]>([]);
  protected readonly teams = signal<TeamResponse[]>([]);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    orgRole: ['ORG_MEMBER' as OrgRole, Validators.required],
    teamId: [''],
    teamRole: ['TEAM_MEMBER' as TeamRole],
  });

  private get orgId(): string {
    return this.session.activeOrganisation()?.orgId ?? '';
  }

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected label(invitation: InvitationResponse): string {
    switch (invitation.status) {
      case 'ACCEPTED':
        return 'Accepted';
      case 'REVOKED':
        return 'Withdrawn';
      case 'EXPIRED':
        return 'Expired';
      default:
        return 'Awaiting reply';
    }
  }

  protected statusClass(invitation: InvitationResponse): string {
    if (invitation.status === 'ACCEPTED') return 'badge badge--success';
    if (invitation.status === 'PENDING') return 'badge badge--accent';
    return 'badge badge--muted';
  }

  protected async invite(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.sentTo.set(null);

    const { email, orgRole, teamId, teamRole } = this.form.getRawValue();
    try {
      await firstValueFrom(
        this.api.createInvitation(this.orgId, {
          email: email.trim(),
          orgRole,
          // The server rejects a team role without a team, so neither is sent unless one is chosen.
          teamId: teamId || undefined,
          teamRole: teamId ? teamRole : undefined,
        }),
      );
      this.sentTo.set(email.trim());
      this.form.reset({
        email: '',
        orgRole: 'ORG_MEMBER',
        teamId: '',
        teamRole: 'TEAM_MEMBER',
      });
      await this.load();
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not send the invitation.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async revoke(invitation: InvitationResponse): Promise<void> {
    if (!confirm(`Withdraw the invitation to ${invitation.email}? Their link will stop working.`)) {
      return;
    }
    this.error.set(null);
    try {
      await firstValueFrom(this.api.revokeInvitation(this.orgId, invitation.id!));
      await this.load();
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not withdraw that invitation.'));
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [invitations, teams] = await Promise.all([
        firstValueFrom(this.api.listInvitations(this.orgId)),
        firstValueFrom(this.teamsApi.listTeams(this.orgId)),
      ]);
      this.invitations.set(invitations);
      this.teams.set(teams);
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
}
