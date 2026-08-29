import { bootstrapApplication } from '@angular/platform-browser';
import { Amplify } from 'aws-amplify';
import { cognitoUserPoolsTokenProvider } from 'aws-amplify/auth/cognito';
import { sessionStorage } from 'aws-amplify/utils';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { loadAppConfig } from './app/core/app-config';

loadAppConfig()
  .then((config) => {
    Amplify.configure({
      Auth: {
        Cognito: {
          userPoolId: config.cognito.userPoolId,
          userPoolClientId: config.cognito.userPoolClientId,
        },
      },
    });

    // Tokens live in sessionStorage, so closing the tab ends the session. Clinical data on a
    // shared ward machine is the reason; it costs a sign-in per browser session.
    cognitoUserPoolsTokenProvider.setKeyValueStorage(sessionStorage);

    return bootstrapApplication(App, appConfig(config));
  })
  .catch((error) => {
    console.error(error);
    document.body.innerHTML =
      '<main class="fatal"><h1>This application is not configured</h1>' +
      '<p>config.json could not be loaded. Please contact your administrator.</p></main>';
  });
