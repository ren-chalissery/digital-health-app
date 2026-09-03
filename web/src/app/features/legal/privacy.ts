import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-privacy',
  imports: [RouterLink],
  styleUrl: './legal.scss',
  template: `
    <article class="legal">
      <p class="legal__brand">Simplicity training</p>

      <div class="legal__card">
        <h1>Privacy policy</h1>
        <p class="legal__meta">Effective 3 September 2026</p>

        <p>
          Simplicity training helps mental health professionals complete organisation-delivered
          training on the Simplicity digital therapeutic. This policy describes how we handle
          personal information when you use the web, iOS, and Android apps at
          <strong>app.simplicityhelp.com</strong> and related services.
        </p>

        <h2>Who we are</h2>
        <p>
          The service is operated for Simplicity training. For privacy enquiries, contact
          <a href="mailto:privacy@simplicityhelp.com">privacy@simplicityhelp.com</a>.
        </p>

        <h2>Information we collect</h2>
        <p>When you create an account and use the service, we may collect:</p>
        <ul>
          <li>
            <strong>Account details</strong> — email address and password (passwords are handled by
            our identity provider; we do not store them in plain text).
          </li>
          <li>
            <strong>Profile information</strong> — name and other details you provide during
            onboarding or in settings.
          </li>
          <li>
            <strong>Organisation membership</strong> — which organisation and teams you belong to,
            and invitations you accept or send (if you are an administrator).
          </li>
          <li>
            <strong>Training activity</strong> — modules assigned to you, progress through
            training content, and quiz responses.
          </li>
          <li>
            <strong>Reflections</strong> — journal entries you create in the Reflect feature. These
            are private to you and are not shared with your organisation or used to answer
            assistant questions.
          </li>
          <li>
            <strong>Assistant questions</strong> — questions you ask about published training
            content. These are processed to generate answers and citations; they are not combined
            with your reflections.
          </li>
          <li>
            <strong>Technical and security data</strong> — authentication events, API requests, and
            limited audit records (such as IP addresses on some administrative actions, which are
            removed after 180 days).
          </li>
        </ul>

        <h2>How we use information</h2>
        <p>We use personal information to:</p>
        <ul>
          <li>provide, secure, and maintain the training service;</li>
          <li>authenticate you and manage your account;</li>
          <li>deliver assigned training, track completion, and show your reflections to you;</li>
          <li>answer questions about published training content through the assistant;</li>
          <li>send service email (such as invitations and account-related messages); and</li>
          <li>investigate abuse, enforce limits, and keep the service reliable.</li>
        </ul>
        <p>We do not sell your personal information.</p>

        <h2>Where data is stored and processed</h2>
        <p>
          The service runs on Amazon Web Services in the <strong>Asia Pacific (Sydney)</strong>
          region (<code>ap-southeast-2</code>). We use AWS services including Cognito (sign-in),
          secure storage, email delivery, media processing, and managed AI inference for the
          assistant. Data stays within our controlled infrastructure except where AWS subprocessors
          are required to operate those services.
        </p>

        <h2>Sharing</h2>
        <p>
          Organisation administrators can see membership, assignments, and training progress for
          their organisation. They cannot see your private reflections. We may disclose information
          if required by law or to protect the security and integrity of the service.
        </p>

        <h2>Retention</h2>
        <p>
          We keep account and training data while your account is active and as needed to operate
          the service. Audit records that include IP addresses are cleared after 180 days. You may
          request deletion of your account by contacting us; some records may be retained where
          required for security, legal, or backup purposes.
        </p>

        <h2>Security</h2>
        <p>
          Access to the API requires authenticated tokens. Reflections are scoped to your account.
          Transport is encrypted in transit (HTTPS). No system is perfectly secure; please use a
          strong, unique password and keep your device secure.
        </p>

        <h2>Children</h2>
        <p>
          The service is intended for trained professionals and is not directed at children under
          16. We do not knowingly collect personal information from children.
        </p>

        <h2>Health disclaimer</h2>
        <p>
          Simplicity training is professional education software. It does not provide medical
          advice, diagnosis, or treatment, and is not a substitute for clinical judgement or
          emergency care.
        </p>

        <h2>Changes</h2>
        <p>
          We may update this policy from time to time. The effective date at the top will change
          when we do. Continued use of the service after an update means you accept the revised
          policy.
        </p>

        <h2>Contact</h2>
        <p>
          Questions about this policy:
          <a href="mailto:privacy@simplicityhelp.com">privacy@simplicityhelp.com</a>.
        </p>
      </div>

      <p class="legal__footer">
        <a routerLink="/sign-in">Sign in</a>
      </p>
    </article>
  `,
})
export class Privacy {}
