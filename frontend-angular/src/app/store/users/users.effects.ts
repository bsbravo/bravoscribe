import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of, switchMap, tap } from 'rxjs';
import { AdminApiService } from '../../core/services/admin-api.service';
import { UsersActions } from './users.actions';

@Injectable()
export class UsersEffects {
  private readonly actions$ = inject(Actions);
  private readonly adminApi = inject(AdminApiService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  loadPage$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UsersActions.pageRequested),
      switchMap(({ page, size }) =>
        this.adminApi.listUsers(page, size).pipe(
          map((res) => UsersActions.pageLoaded(res)),
          catchError((err: HttpErrorResponse) =>
            of(UsersActions.pageLoadFailure({ error: err.message ?? 'Failed to load users' })),
          ),
        ),
      ),
    ),
  );

  deactivate$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UsersActions.deactivateRequested),
      exhaustMap(({ id }) =>
        this.adminApi.deactivateUser(id).pipe(
          map(() => UsersActions.deactivateSuccess({ id })),
          catchError((err: HttpErrorResponse) =>
            of(UsersActions.deactivateFailure({ id, error: err.message ?? 'Failed to deactivate user' })),
          ),
        ),
      ),
    ),
  );

  onDeactivateSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(UsersActions.deactivateSuccess),
        tap(() => {
          this.snackBar.open('Account deactivated', 'Dismiss', { duration: 4000 });
          this.router.navigate(['/users']);
        }),
      ),
    { dispatch: false },
  );
}
