import { createReducer, on } from '@ngrx/store';
import { decodeAccessToken } from '../../core/utils/jwt.util';
import { AuthActions } from './auth.actions';
import { AuthState, initialAuthState } from './auth.state';

export const authReducer = createReducer(
  initialAuthState,

  on(AuthActions.appInitialized, (state): AuthState => ({ ...state, status: 'checking' })),

  on(AuthActions.sessionRestored, (state, { accessToken }): AuthState => {
    const claims = decodeAccessToken(accessToken);
    return {
      ...state,
      status: 'authenticated',
      accessToken,
      userId: claims?.sub ?? null,
      role: claims?.role ?? null,
    };
  }),

  on(AuthActions.sessionAbsent, (state): AuthState => ({ ...initialAuthState, status: 'anonymous' })),

  on(AuthActions.loginSubmitted, (state): AuthState => ({ ...state, loginPending: true, loginError: null })),

  on(AuthActions.loginSuccess, (state, { accessToken }): AuthState => {
    const claims = decodeAccessToken(accessToken);
    return {
      ...state,
      status: 'authenticated',
      accessToken,
      userId: claims?.sub ?? null,
      role: claims?.role ?? null,
      loginPending: false,
      loginError: null,
    };
  }),

  on(AuthActions.loginFailure, (state, { message }): AuthState => ({
    ...state,
    status: 'anonymous',
    loginPending: false,
    loginError: message,
  })),

  on(AuthActions.loginForbidden, (state): AuthState => ({
    ...state,
    status: 'anonymous',
    accessToken: null,
    userId: null,
    role: null,
    loginPending: false,
    loginError: 'This account does not have admin access.',
  })),

  on(AuthActions.sessionEnded, (): AuthState => ({ ...initialAuthState, status: 'anonymous' })),
);
