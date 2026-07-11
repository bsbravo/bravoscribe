import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AsyncPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { UsersActions } from '../../../store/users/users.actions';
import {
  selectFilteredUsers,
  selectSearchTerm,
  selectUsersLoading,
  selectUsersPage,
  selectUsersSize,
  selectUsersTotalElements,
} from '../../../store/users/users.selectors';

@Component({
  selector: 'app-user-list',
  imports: [
    AsyncPipe,
    DatePipe,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);

  protected readonly displayedColumns = ['name', 'email', 'role', 'status', 'createdAt'];

  protected readonly users$ = this.store.select(selectFilteredUsers);
  protected readonly loading$ = this.store.select(selectUsersLoading);
  protected readonly page$ = this.store.select(selectUsersPage);
  protected readonly size$ = this.store.select(selectUsersSize);
  protected readonly totalElements$ = this.store.select(selectUsersTotalElements);
  protected readonly searchTerm$ = this.store.select(selectSearchTerm);

  ngOnInit(): void {
    this.store.dispatch(UsersActions.pageRequested({ page: 0, size: 31 }));
  }

  onSearchChange(term: string): void {
    this.store.dispatch(UsersActions.searchTermChanged({ term }));
  }

  onPage(event: PageEvent): void {
    this.store.dispatch(UsersActions.pageRequested({ page: event.pageIndex, size: event.pageSize }));
  }

  openUser(id: string): void {
    this.router.navigate(['/users', id]);
  }
}
