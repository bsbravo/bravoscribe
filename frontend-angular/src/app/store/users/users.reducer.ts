import { createReducer, on } from '@ngrx/store';
import { UsersActions } from './users.actions';
import { initialUsersState, usersAdapter, UsersState } from './users.state';

export const usersReducer = createReducer(
  initialUsersState,

  on(UsersActions.pageRequested, (state, { page, size }): UsersState => ({
    ...state,
    page,
    size,
    loading: true,
    error: null,
  })),

  on(UsersActions.pageLoaded, (state, { content, number, size, totalElements, totalPages }): UsersState =>
    usersAdapter.setAll(content, {
      ...state,
      page: number,
      size,
      totalElements,
      totalPages,
      loading: false,
      error: null,
    }),
  ),

  on(UsersActions.pageLoadFailure, (state, { error }): UsersState => ({
    ...state,
    loading: false,
    error,
  })),

  on(UsersActions.deactivateRequested, (state, { id }): UsersState => ({
    ...state,
    deactivatingId: id,
    error: null,
  })),

  on(UsersActions.deactivateSuccess, (state, { id }): UsersState =>
    usersAdapter.updateOne({ id, changes: { active: false } }, { ...state, deactivatingId: null }),
  ),

  on(UsersActions.deactivateFailure, (state, { error }): UsersState => ({
    ...state,
    deactivatingId: null,
    error,
  })),

  on(UsersActions.searchTermChanged, (state, { term }): UsersState => ({
    ...state,
    searchTerm: term,
  })),
);
