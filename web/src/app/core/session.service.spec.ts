import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { CurrentUserApi } from '../api/api/current-user.service';
import { CurrentUserResponse } from '../api/model/current-user-response';
import { OrganisationMembershipResponse } from '../api/model/organisation-membership-response';
import { AuthService } from './auth/auth.service';
import { SessionService } from './session.service';

function membership(orgId: string, name: string): OrganisationMembershipResponse {
  return { orgId, name, slug: name, organisationType: 'CLINIC', orgRole: 'ORG_MEMBER', teams: [] };
}

function user(partial: Partial<CurrentUserResponse>): CurrentUserResponse {
  return {
    id: 'user-1',
    email: 'ada@example.org',
    profileCompleted: true,
    status: 'ACTIVE',
    platformRole: 'STANDARD',
    organisations: [],
    ...partial,
  } as CurrentUserResponse;
}

function sessionWith(current: CurrentUserResponse): SessionService {
  TestBed.configureTestingModule({
    providers: [
      SessionService,
      { provide: CurrentUserApi, useValue: {} },
      { provide: AuthService, useValue: {} },
    ],
  });
  const session = TestBed.inject(SessionService);
  session.set(current);
  return session;
}

describe('SessionService.activeOrganisation', () => {
  it('follows the choice the server recorded', () => {
    const session = sessionWith(
      user({
        organisations: [membership('a', 'Alpha'), membership('b', 'Beta')],
        activeOrganisationId: 'b',
      }),
    );

    expect(session.activeOrganisation()?.orgId).toBe('b');
    expect(session.canSwitchOrganisation()).toBe(true);
  });

  it('falls back to the first when the recorded choice is no longer a membership', () => {
    // What a clinician sees the moment an organisation they were in is archived: the pointer
    // survives, the membership does not, and landing nowhere would trap them on a blank screen.
    const session = sessionWith(
      user({ organisations: [membership('a', 'Alpha')], activeOrganisationId: 'archived' }),
    );

    expect(session.activeOrganisation()?.orgId).toBe('a');
  });

  it('falls back to the first for somebody who has never switched', () => {
    const session = sessionWith(user({ organisations: [membership('a', 'Alpha')] }));

    expect(session.activeOrganisation()?.orgId).toBe('a');
    expect(session.canSwitchOrganisation()).toBe(false);
  });

  it('has no active organisation before onboarding finishes', () => {
    const session = sessionWith(user({ organisations: [] }));

    expect(session.activeOrganisation()).toBeNull();
  });
});
