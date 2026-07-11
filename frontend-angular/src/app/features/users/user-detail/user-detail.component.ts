import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AsyncPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { combineLatest, map } from 'rxjs';
import { UsersActions } from '../../../store/users/users.actions';
import { selectUserById, selectUsersLoading } from '../../../store/users/users.selectors';
import { DeactivateDialogComponent } from '../deactivate-dialog/deactivate-dialog.component';

@Component({
  selector: 'app-user-detail',
  imports: [
    AsyncPipe,
    DatePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './user-detail.component.html',
  styleUrl: './user-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  private readonly userId = this.route.snapshot.paramMap.get('id')!;

  protected readonly user$ = this.store.select(selectUserById(this.userId));
  protected readonly loading$ = this.store.select(selectUsersLoading);

  // The User Service has no "get user by id" endpoint (see admin-api.service.ts), so a
  // direct/refreshed navigation to /users/:id needs the full list loaded first to find
  // this one user in it. If it's still missing once loading finishes, the user genuinely
  // doesn't exist (or the id is wrong) and the template shows a "not found" state.
  protected readonly notFound$ = combineLatest([this.user$, this.loading$]).pipe(
    map(([user, loading]) => !user && !loading),
  );

  ngOnInit(): void {
    this.store.dispatch(UsersActions.pageRequested({ page: 0, size: 100 }));
  }

  deactivate(): void {
    const dialogRef = this.dialog.open(DeactivateDialogComponent);
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.store.dispatch(UsersActions.deactivateRequested({ id: this.userId }));
      }
    });
  }
}
