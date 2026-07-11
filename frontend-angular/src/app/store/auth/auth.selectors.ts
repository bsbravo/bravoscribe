import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AUTH_FEATURE_KEY, AuthState } from './auth.state';

export const selectAuthState = createFeatureSelector<AuthState>(AUTH_FEATURE_KEY);

export const selectAuthStatus = createSelector(selectAuthState, (state) => state.status);
export const selectAccessToken = createSelector(selectAuthState, (state) => state.accessToken);
export const selectRole = createSelector(selectAuthState, (state) => state.role);
export const selectLoginPending = createSelector(selectAuthState, (state) => state.loginPending);
export const selectLoginError = createSelector(selectAuthState, (state) => state.loginError);

export const selectIsAdmin = createSelector(
  selectAuthState,
  (state) => state.status === 'authenticated' && state.role === 'ADMIN',
);
