import { createFeatureSelector, createSelector } from '@ngrx/store';
import { USERS_FEATURE_KEY, UsersState, usersAdapter } from './users.state';

export const selectUsersState = createFeatureSelector<UsersState>(USERS_FEATURE_KEY);

const { selectAll } = usersAdapter.getSelectors();

export const selectAllUsers = createSelector(selectUsersState, selectAll);
export const selectUsersLoading = createSelector(selectUsersState, (state) => state.loading);
export const selectUsersError = createSelector(selectUsersState, (state) => state.error);
export const selectUsersPage = createSelector(selectUsersState, (state) => state.page);
export const selectUsersSize = createSelector(selectUsersState, (state) => state.size);
export const selectUsersTotalElements = createSelector(selectUsersState, (state) => state.totalElements);
export const selectUsersTotalPages = createSelector(selectUsersState, (state) => state.totalPages);
export const selectDeactivatingId = createSelector(selectUsersState, (state) => state.deactivatingId);
export const selectSearchTerm = createSelector(selectUsersState, (state) => state.searchTerm);

// The User Service list endpoint (GET /api/users) has no `q` search parameter — it only
// accepts page/size (see services/user-service AdminController). Search is therefore
// applied client-side over whatever page is currently loaded, not across the whole table.
export const selectFilteredUsers = createSelector(selectAllUsers, selectSearchTerm, (users, term) => {
  const needle = term.trim().toLowerCase();
  if (!needle) {
    return users;
  }
  return users.filter(
    (user) => user.name.toLowerCase().includes(needle) || user.email.toLowerCase().includes(needle),
  );
});

export const selectUserById = (id: string) =>
  createSelector(selectAllUsers, (users) => users.find((user) => user.id === id) ?? null);

// Dashboard stats. Accurate as long as the single fetched page contains every user —
// true for this personal/learning-scale app (page size capped at the server max of 100,
// see AdminController#listUsers). There is no dedicated stats endpoint on the User
// Service, so this is the best available signal from the existing API surface.
export const selectTotalUsers = selectUsersTotalElements;

export const selectNewUsersThisWeek = createSelector(selectAllUsers, (users) => {
  const oneWeekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
  return users.filter((user) => new Date(user.createdAt).getTime() >= oneWeekAgo).length;
});

export const selectDeactivatedUsers = createSelector(
  selectAllUsers,
  (users) => users.filter((user) => !user.active).length,
);
