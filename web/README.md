# Web client

Angular application for the Simplicity training package.

## Running it

```bash
nvm use          # Node 24, pinned in ../.nvmrc
npm install
npm start        # http://localhost:4200
```

The backend has to be running on `http://localhost:8080` and a Cognito user pool has to exist —
`Amplify.configure` needs a real pool, so there is no offline mode for the auth screens.

## Configuration

`public/config.json` is read at start-up, before Angular boots:

```json
{
  "apiBaseUrl": "http://localhost:8080",
  "cognito": { "userPoolId": "...", "userPoolClientId": "..." }
}
```

It is deliberately not compiled in, so the same bundle can be promoted between environments and
the deployment writes the file. The committed copy has empty Cognito ids; fill them in locally
from the auth stack's outputs. A missing or incomplete file stops the application with a message
rather than producing a blank page.

## How the pieces fit together

- **`src/app/api/`** is generated from `api-contract/openapi.yaml`. Do not edit it; see
  [../api-contract/README.md](../api-contract/README.md).
- **`core/auth/auth.service.ts`** is the only file that talks to Cognito. Tokens are held in
  `sessionStorage`, so closing the tab ends the session.
- **`core/auth/auth.interceptor.ts`** attaches the access token to every request except the public
  invitation preview, and signs the user out on a 401.
- **`core/session.service.ts`** caches `GET /api/v1/me`. Onboarding state comes from the server, so
  the web, iOS, and Android clients cannot disagree about when setup is finished.
- **`core/auth/guards.ts`** routes on that state: no profile means the wizard, no organisation
  means the organisation step, and administrator pages are not offered to ordinary members. The
  server enforces all of it regardless; the guards are about not showing a page that would only
  produce a 403.

## Routes

| Route | Who |
| --- | --- |
| `/sign-in`, `/sign-up`, `/confirm-email`, `/forgot-password` | Anyone |
| `/invitations/:token` | Anyone — the recipient usually has no account yet |
| `/welcome/profile`, `/welcome/organisation` | Signed in, still setting up |
| `/dashboard`, `/learn`, `/reflect`, `/settings` | Set up |
| `/settings/members`, `/settings/teams`, `/settings/invitations` | Organisation administrators |

## Tests

```bash
npm test
```
