import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { from, switchMap, tap, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

/** Endpoints the recipient of an invitation reaches before they have an account. */
const PUBLIC_PATHS = [/\/api\/v1\/invitations\/[^/]+$/];

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (PUBLIC_PATHS.some((pattern) => pattern.test(request.url)) && request.method === 'GET') {
    return next(request);
  }

  return from(auth.accessToken()).pipe(
    switchMap((token) =>
      next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request),
    ),
    catchError((error) => {
      // 401 means the token was rejected outright — expired beyond refresh, or the session was
      // revoked server-side. Either way the only way forward is to sign in again.
      if (error?.status === 401) {
        void auth.signOut().finally(() => router.navigate(['/sign-in']));
      }
      return throwError(() => error);
    }),
  );
};
