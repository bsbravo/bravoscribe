import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of, tap } from 'rxjs';
import { AuthApiService } from '../../core/services/auth-api.service';
import { decodeAccessToken } from '../../core/utils/jwt.util';
import { AuthActions } from './auth.actions';

@Injectable()
export class AuthEffects {
  private readonly actions$ = inject(Actions);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  // On app bootstrap, try to turn the httpOnly refresh cookie (if any) into a fresh
  // access token. This is what lets an admin reload the page without being bounced to
  // /login — the access token itself is never persisted (kept in the NgRx store, in
  // memory only), only the server-held httpOnly cookie survives a reload.
  restoreSession$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.appInitialized),
      exhaustMap(() =>
        this.authApi.refresh().pipe(
          map((res) => AuthActions.sessionRestored({ accessToken: res.accessToken })),
          catchError(() => of(AuthActions.sessionAbsent())),
        ),
      ),
    ),
  );

  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loginSubmitted),
      exhaustMap(({ email, password }) =>
        this.authApi.login(email, password).pipe(
          map((res) => {
            const claims = decodeAccessToken(res.accessToken);
            if (claims?.role !== 'ADMIN') {
              return AuthActions.loginForbidden();
            }
            return AuthActions.loginSuccess({ accessToken: res.accessToken });
          }),
          catchError((err: HttpErrorResponse) => {
            const message =
              err.status === 401 ? 'Invalid email or password' : 'Something went wrong. Please try again.';
            return of(AuthActions.loginFailure({ message }));
          }),
        ),
      ),
    ),
  );

  // A valid-but-non-admin login still creates a real refreshToken cookie server-side.
  // Immediately revoke it so no non-admin session lingers in this admin-only app.
  revokeForbiddenSession$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loginForbidden),
      exhaustMap(() => this.authApi.logout().pipe(catchError(() => of(void 0)))),
    ),
  { dispatch: false },
  );

  logout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.logoutRequested),
      exhaustMap(() =>
        this.authApi.logout().pipe(
          map(() => AuthActions.sessionEnded({ reason: 'logout' })),
          // Best-effort: even if the network call fails, end the local session.
          catchError(() => of(AuthActions.sessionEnded({ reason: 'logout' }))),
        ),
      ),
    ),
  );

  redirectOnLoginSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.loginSuccess),
        tap(() => this.router.navigate(['/dashboard'])),
      ),
    { dispatch: false },
  );

  redirectOnSessionEnded$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.sessionEnded),
        tap(({ reason }) => {
          const queryParams = reason === 'expired' ? { reason: 'session-expired' } : {};
          this.router.navigate(['/login'], { queryParams });
        }),
      ),
    { dispatch: false },
  );
}
