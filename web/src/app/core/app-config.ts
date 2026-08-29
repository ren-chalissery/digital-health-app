import { InjectionToken } from '@angular/core';

/**
 * Read from `/config.json` at start-up rather than baked in at build time, so one bundle can be
 * promoted between environments and the deployment writes the file.
 */
export interface AppConfig {
  apiBaseUrl: string;
  cognito: {
    userPoolId: string;
    userPoolClientId: string;
    /** Set only for local development, to reach the Floci emulator instead of Cognito itself. */
    endpoint?: string;
  };
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export async function loadAppConfig(): Promise<AppConfig> {
  const response = await fetch('config.json', { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`Could not load config.json (${response.status})`);
  }

  const config = (await response.json()) as Partial<AppConfig>;
  if (!config.apiBaseUrl || !config.cognito?.userPoolId || !config.cognito?.userPoolClientId) {
    // Failing loudly here beats a blank page and an unexplained 401 on the first request.
    throw new Error('config.json is missing apiBaseUrl or Cognito user pool details');
  }
  return config as AppConfig;
}
