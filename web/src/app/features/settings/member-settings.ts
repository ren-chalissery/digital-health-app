import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OrganisationsApi } from '../../api/api/organisations.service';
import { ChangeOrgRoleRequest } from '../../api/model/change-org-role-request';
import { OrgMemberResponse } from '../../api/model/org-member-response';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'app-member-settings',
  template: `
    <div class="card">
      <div class="card__title"><h2>People</h2></div>

      @if (error()) {
        <p class="notice notice--error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <p>Loading people…</p>
      } @else {
        <table class="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Role</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (member of members(); track member.userId) {
              <tr>
                <td>
                  <strong>{{ member.fullName || member.email }}</strong>
                  <div class="field__hint">
                    {{ member.email }}@if (member.professionalRole) {
                      · {{ member.professionalRole }}
                    }
                  </div>
                </td>
                <td>
                  <span class="badge" [class.badge--accent]="member.orgRole === 'ORG_ADMIN'">
                    {{ member.orgRole === 'ORG_ADMIN' ? 'Administrator' : 'Member' }}
                  </span>
                </td>
                <td>
                  <span class="badge" [class.badge--success]="member.userStatus === 'ACTIVE'">
                    {{ member.userStatus === 'INVITED' ? 'Invited' : 'Active' }}
                  </span>
                </td>
                <td class="numeric">
                  @if (member.userId !== currentUserId()) {
                    <button class="button--link" type="button" (click)="toggleRole(member)">
                      {{ member.orgRole === 'ORG_ADMIN' ? 'Make member' : 'Make administrator' }}
                    </button>
                    <button class="button button--danger" type="button" (click)="remove(member)">
                      Remove
                    </button>
                  } @else {
                    <span class="field__hint">You</span>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>

    <div class="card">
      <div class="card__title"><h2>Archive this organisation</h2></div>
      <p class="field__hint">
        {{ organisationName() }} will disappear for everyone in it, including you. Nothing is
        deleted — memberships, teams, and the record of who did what are all kept — but nobody will
        be able to reach it again.
      </p>

      @if (archiveError()) {
        <p class="notice notice--error" role="alert">{{ archiveError() }}</p>
      }

      <div class="field">
        <label for="confirmName">Type <strong>{{ organisationName() }}</strong> to confirm</label>
        <input id="confirmName" [value]="typedName()" (input)="onTypedName($event)" />
      </div>

      <div class="row">
        <button
          class="button button--danger"
          type="button"
          [disabled]="archiving() || typedName() !== organisationName()"
          (click)="archive()"
        >
          {{ archiving() ? 'Archiving…' : 'Archive organisation' }}
        </button>
      </div>
    </div>
  `,
})
export class MemberSettings implements OnInit {
  private readonly api = inject(OrganisationsApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly members = signal<OrgMemberResponse[]>([]);
  protected readonly archiving = signal(false);
  protected readonly archiveError = signal<string | null>(null);
  // Typing the name is deliberate friction: this one affects colleagues, not just the person
  // clicking it.
  protected readonly typedName = signal('');

  private get orgId(): string {
    return this.session.activeOrganisation()?.orgId ?? '';
  }

  protected currentUserId(): string | undefined {
    return this.session.user()?.id;
  }

  protected organisationName(): string {
    return this.session.activeOrganisation()?.name ?? '';
  }

  protected onTypedName(event: Event): void {
    this.typedName.set((event.target as HTMLInputElement).value);
  }

  protected async archive(): Promise<void> {
    if (this.archiving() || this.typedName() !== this.organisationName()) {
      return;
    }
    this.archiving.set(true);
    this.archiveError.set(null);
    try {
      await firstValueFrom(this.api.archiveOrganisation(this.orgId));
      const user = await this.session.refresh();
      await this.router.navigateByUrl(
        (user.organisations ?? []).length > 0 ? '/dashboard' : '/welcome/organisation',
      );
    } catch (error) {
      this.archiveError.set(problemMessage(error, 'Could not archive the organisation.'));
    } finally {
      this.archiving.set(false);
    }
  }

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected async toggleRole(member: OrgMemberResponse): Promise<void> {
    const orgRole: ChangeOrgRoleRequest['orgRole'] =
      member.orgRole === 'ORG_ADMIN' ? 'ORG_MEMBER' : 'ORG_ADMIN';
    this.error.set(null);
    try {
      await firstValueFrom(this.api.changeOrganisationRole(this.orgId, member.userId!, { orgRole }));
      await this.load();
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not change that role.'));
    }
  }

  protected async remove(member: OrgMemberResponse): Promise<void> {
    const who = member.fullName || member.email;
    if (!confirm(`Remove ${who}? They will also leave every team in this organisation.`)) {
      return;
    }
    this.error.set(null);
    try {
      await firstValueFrom(this.api.removeOrganisationMember(this.orgId, member.userId!));
      await this.load();
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not remove that person.'));
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.members.set(await firstValueFrom(this.api.listOrganisationMembers(this.orgId)));
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
}
