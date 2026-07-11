import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Store } from '@ngrx/store';
import { catchError, throwError } from 'rxjs';
import { AuthActions } from '../../store/auth/auth.actions';

// Requests where a 401 is an expected, locally-handled outcome (bad credentials on the
// login page itself) rather than "your session died" — these must NOT trigger the
// global session-expired redirect.
const AUTH_ENDPOINTS = ['/api/users/login', '/api/users/refresh'];

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(Store);
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => req.url.includes(path));

        if (err.status === 401 && !isAuthEndpoint) {
          store.dispatch(AuthActions.sessionEnded({ reason: 'expired' }));
        }

        // A 403 from an admin endpoint means the JWT is valid but lacks ADMIN
        // authority (services/user-service AdminController @PreAuthorize check).
        // This is the server-side enforcement the client-side guard only mirrors.
        if (err.status === 403) {
          snackBar.open('You do not have permission to perform this action.', 'Dismiss', { duration: 5000 });
          store.dispatch(AuthActions.sessionEnded({ reason: 'forbidden' }));
        }
      }
      return throwError(() => err);
    }),
  );
};
