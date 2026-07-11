import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { UsersActions } from '../../store/users/users.actions';
import {
  selectDeactivatedUsers,
  selectNewUsersThisWeek,
  selectTotalUsers,
  selectUsersLoading,
} from '../../store/users/users.selectors';

@Component({
  selector: 'app-dashboard',
  imports: [AsyncPipe, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private readonly store = inject(Store);

  protected readonly loading$ = this.store.select(selectUsersLoading);
  protected readonly totalUsers$ = this.store.select(selectTotalUsers);
  protected readonly newThisWeek$ = this.store.select(selectNewUsersThisWeek);
  protected readonly deactivated$ = this.store.select(selectDeactivatedUsers);

  ngOnInit(): void {
    // Page size 100 = server max (AdminController caps at Math.min(size, 100)).
    // There is no dedicated stats endpoint on the User Service, so the dashboard derives
    // its numbers from the full user list — see users.selectors.ts for the caveat.
    this.store.dispatch(UsersActions.pageRequested({ page: 0, size: 100 }));
  }
}
