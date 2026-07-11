import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { from, switchMap, firstValueFrom } from 'rxjs';
import { selectAccessToken } from '../../store/auth/auth.selectors';

/**
 * Attaches the in-memory access token (never localStorage/sessionStorage — see
 * SYSTEM_SPEC.md §5 and the equivalent discipline used by frontend-react's Zustand
 * store) as a Bearer token on every outgoing request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(Store);

  return from(firstValueFrom(store.select(selectAccessToken))).pipe(
    switchMap((token) => {
      if (!token) {
        return next(req);
      }
      return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
    }),
  );
};
