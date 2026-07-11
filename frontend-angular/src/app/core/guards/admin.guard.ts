import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { combineLatest, filter, map, take } from 'rxjs';
import { selectAuthStatus, selectIsAdmin } from '../../store/auth/auth.selectors';

/**
 * Client-side UX guard: keeps a non-admin (or unauthenticated) visitor from ever seeing
 * the admin shell. This is a convenience only — the real authorization boundary is the
 * User Service's @PreAuthorize("hasAuthority('ADMIN')") on every admin endpoint, which
 * rejects a USER-role token with 403 regardless of what this guard decides.
 *
 * Waits out the 'checking' status (silent session restore via the refresh cookie) so a
 * page reload doesn't bounce an already-authenticated admin to /login.
 */
export const adminGuard: CanActivateFn = () => {
  const store = inject(Store);
  const router = inject(Router);

  return combineLatest([store.select(selectAuthStatus), store.select(selectIsAdmin)]).pipe(
    filter(([status]) => status !== 'checking'),
    take(1),
    map(([, isAdmin]) => isAdmin || router.createUrlTree(['/login'])),
  );
};

/** Keeps an already-authenticated admin from landing back on the login screen. */
export const loginGuard: CanActivateFn = () => {
  const store = inject(Store);
  const router = inject(Router);

  return combineLatest([store.select(selectAuthStatus), store.select(selectIsAdmin)]).pipe(
    filter(([status]) => status !== 'checking'),
    take(1),
    map(([, isAdmin]) => (isAdmin ? router.createUrlTree(['/dashboard']) : true)),
  );
};
