import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { InvitationsApi } from '../../api/api/invitations.service';
import { InvitationPreviewResponse } from '../../api/model/invitation-preview-response';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/problem';
import { SessionService } from '../../core/session.service';

/**
 * The landing page for an invitation link.
 *
 * <p>Reachable signed out, because the recipient usually has no account yet: the preview shows who
 * invited them so they know what they are signing up for before creating an account.
 */
@Component({
  selector: 'app-accept-invitation',
  imports: [RouterLink],
  styleUrl: '../auth/auth-shell.scss',
  template: `
    <div class="auth">
      <p class="auth__brand">Simplicity training</p>
      <div class="auth__card">
        @if (loading()) {
          <p>Checking your invitation…</p>
        } @else if (!preview()?.valid) {
          <h1>This invitation is no longer valid</h1>
          <p class="auth__lede">
            It may have expired, been withdrawn, or already been used. Ask whoever invited you to
            send a new one.
          </p>
          <a class="button button--secondary" routerLink="/sign-in">Go to sign in</a>
        } @else {
          <h1>Join {{ preview()!.organisationName }}</h1>
          <p class="auth__lede">
            You have been invited as
            {{ preview()!.orgRole === 'ORG_ADMIN' ? 'an administrator' : 'a member' }}@if (
              preview()!.teamName
            ) {, in the team {{ preview()!.teamName }}}.
          </p>

          @if (error()) {
            <p class="notice notice--error" role="alert">{{ error() }}</p>
          }

          @if (signedIn()) {
            <div class="auth__actions">
              <button class="button" type="button" [disabled]="busy()" (click)="accept()">
                {{ busy() ? 'Joining…' : 'Accept invitation' }}
              </button>
            </div>
          } @else {
            <p class="notice notice--info">
              Sign in or create an account with <strong>{{ preview()!.email }}</strong> to accept.
              The invitation only works for that address.
            </p>
            <div class="auth__actions stack">
              <a class="button" [routerLink]="['/sign-up']" [queryParams]="{ next: currentUrl }">
                Create an account
              </a>
              <a
                class="button button--secondary"
                [routerLink]="['/sign-in']"
                [queryParams]="{ next: currentUrl }"
              >
                Sign in
              </a>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class AcceptInvitation implements OnInit {
  private readonly api = inject(InvitationsApi);
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly signedIn = signal(false);
  protected readonly preview = signal<InvitationPreviewResponse | null>(null);
  protected readonly error = signal<string | null>(null);

  protected get currentUrl(): string {
    return `/invitations/${this.token}`;
  }

  async ngOnInit(): Promise<void> {
    this.signedIn.set(await this.auth.isSignedIn());
    try {
      this.preview.set(await firstValueFrom(this.api.previewInvitation(this.token)));
    } catch (error) {
      this.error.set(problemMessage(error));
      this.preview.set({ valid: false });
    } finally {
      this.loading.set(false);
    }
  }

  protected async accept(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    try {
      await firstValueFrom(this.api.acceptInvitation(this.token));
      const user = await this.session.refresh();
      // Somebody invited before they ever signed in still has no profile.
      await this.router.navigateByUrl(user.profileCompleted ? '/dashboard' : '/welcome/profile');
    } catch (error) {
      this.error.set(problemMessage(error, 'Could not accept the invitation.'));
    } finally {
      this.busy.set(false);
    }
  }
}
