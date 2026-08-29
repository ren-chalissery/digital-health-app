import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideApi } from './api/provide-api';
import { routes } from './app.routes';
import { AppConfig, APP_CONFIG } from './core/app-config';
import { authInterceptor } from './core/auth/auth.interceptor';

export function appConfig(config: AppConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideRouter(routes, withComponentInputBinding()),
      provideHttpClient(withInterceptors([authInterceptor])),
      provideApi(config.apiBaseUrl),
      { provide: APP_CONFIG, useValue: config },
    ],
  };
}
